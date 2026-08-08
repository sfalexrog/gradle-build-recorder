package com.github.sfalexrog.gradlebuildrecorder

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildRecord
import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildResult
import com.github.sfalexrog.gradlebuildrecorder.models.GradleInvocation
import com.github.sfalexrog.gradlebuildrecorder.recorders.FileDownloadEventRecorder
import com.github.sfalexrog.gradlebuildrecorder.recorders.TaskExecutionEventRecorder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.download.FileDownloadFinishEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
import java.io.File

private class Entrypoint: CliktCommand() {
    val recordPath: File by option("-o", "--output", help = "Output file name")
        .file(canBeFile = true, canBeDir = false, mustExist = false)
        .default(File("build_log_${System.currentTimeMillis()}.json"))

    val gradleProjectPath: File by argument(name = "gradleProjectPath", help = "Path to Gradle project")
        .file(mustExist = true)

    val gradleArgs by argument(name = "Gradle execution arguments")
        .multiple(required = false)

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught", "LongMethod")
    override fun run() {
        val fileDownloadEventRecorder = FileDownloadEventRecorder()
        val taskExecutionEventRecorder = TaskExecutionEventRecorder()

        val connection = GradleConnector.newConnector()
            .forProjectDirectory(gradleProjectPath)
            .connect()

        val taskExecutionListener = { event: ProgressEvent ->
            when(event) {
                is FileDownloadFinishEvent -> fileDownloadEventRecorder.processEvent(event)
                is TaskFinishEvent -> taskExecutionEventRecorder.processEvent(event)
                // drop silently
                else -> {}
            }
        }

        val startTime = System.currentTimeMillis()
        val result: GradleBuildResult = try {
            connection.use { conn ->
                conn.newBuild()
                    .addArguments(gradleArgs)
                    .addProgressListener(taskExecutionListener, setOf(OperationType.TASK, OperationType.FILE_DOWNLOAD))
                    .setStandardError(System.err)
                    .setStandardOutput(System.out)
                    .run()
            }
            GradleBuildResult.Success
        } catch(ex: RuntimeException) {
            when(ex) {
                is UnsupportedVersionException ->
                    GradleBuildResult.UnsupportedVersion(ex.message ?: "[no message]")
                is UnsupportedOperationConfigurationException ->
                    GradleBuildResult.UnsupportedOperationConfiguration(ex.message ?: "[no message]")
                is UnsupportedBuildArgumentException ->
                    GradleBuildResult.UnsupportedBuildArgument(ex.message ?: "[no message]")
                is BuildException ->
                    GradleBuildResult.BuildFailure(ex.message ?: "[no message]")
                is BuildCancelledException ->
                    GradleBuildResult.BuildCancelled(ex.message ?: "[no message]")
                is GradleConnectionException ->
                    GradleBuildResult.GradleConnectionError(ex.message ?: "[no message]")
                else -> throw ex
            }
        }
        val endTime = System.currentTimeMillis()

        recordPath.outputStream().use { output ->
            val json = Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
            val events = GradleBuildRecord(
                invocation = GradleInvocation(
                    projectPath = gradleProjectPath.absolutePath,
                    gradleArgs = gradleArgs,
                    result = result,
                    startTime = startTime,
                    endTime = endTime,
                ),
                fileDownloadEvents = fileDownloadEventRecorder.getRecordedEvents(),
                taskExecutionEvents = taskExecutionEventRecorder.getRecordedEvents(),
            )
            json.encodeToStream<GradleBuildRecord>(events, output)
        }
    }
}

fun main(args: Array<String>) {
    Entrypoint().main(args)
}
