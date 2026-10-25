package com.github.sfalexrog.gradlebuildrecorder

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.file
import com.github.sfalexrog.gradlebuildrecorder.internal.OutputRecorder
import com.github.sfalexrog.gradlebuildrecorder.internal.ProfilerSession
import com.github.sfalexrog.gradlebuildrecorder.internal.writeRecording
import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildRecord
import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildResult
import com.github.sfalexrog.gradlebuildrecorder.models.GradleInvocation
import com.github.sfalexrog.gradlebuildrecorder.recorders.FileDownloadEventRecorder
import com.github.sfalexrog.gradlebuildrecorder.recorders.TaskExecutionEventRecorder
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


private class Entrypoint: CliktCommand() {
    private val execTimestamp = System.currentTimeMillis()

    val recordPath: File by option(
        "-o", "--output", help = "Output file name (.html embeds the viewer; otherwise JSON)",
    )
        .file(canBeFile = true, canBeDir = false, mustExist = false)
        .default(File("build_log_$execTimestamp.json"))

    val gradleProjectPath: File by argument(name = "gradle-project-path", help = "Path to Gradle project")
        .file(mustExist = true)

    val gradleArgs by argument(name = "Gradle execution arguments")
        .multiple(required = false)

    val profilerEnabled by option("-p", "--with-profiler", help = "Run build with async-profiler enabled")
        .boolean()
        .default(false)

    val profilePath: File by option("--profiler-output", help = "Profile output file name")
        .file(canBeFile = true, canBeDir = false, mustExist = false)
        .default(File("profile_${execTimestamp}_%p.jfr"))

    val profilerArgs by option("--profiler-args", help = "Profiler arguments (other than file=)")
        .default("jfr,all")

    val noDaemon by option("--no-daemon", help = "Use a fresh daemon and stop it after the build")
        .flag()

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught", "LongMethod")
    override fun run() {
        val fileDownloadEventRecorder = FileDownloadEventRecorder()
        val taskExecutionEventRecorder = TaskExecutionEventRecorder()

        val profiler = if (profilerEnabled) ProfilerSession(profilePath, profilerArgs) else null
        val singleUse = noDaemon || gradleArgs.lastOrNull {
            it == "--no-daemon" || it == "--daemon" || it.startsWith("-Dorg.gradle.daemon=")
        }?.let { it == "--no-daemon" || it == "-Dorg.gradle.daemon=false" } == true
        val buildArgs = gradleArgs.filterNot {
            it == "--no-daemon" || it == "--daemon" || it.startsWith("-Dorg.gradle.daemon=")
        }
        val connector = GradleConnector.newConnector()
        if (singleUse) ProfilerSession.isolateDaemon(connector)
        val connection = connector
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
        val dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        val prefixFormatter = { ts: Long ->
            val instant = Instant.ofEpochMilli(ts).atZone(ZoneId.of("UTC"))
            val formattedInstant = dateFormat.format(instant)
            "[$formattedInstant] "
        }
        val stdoutRecorder = OutputRecorder(
            sink = System.out,
            prefixFormatter = prefixFormatter,
        )
        val stderrRecorder = OutputRecorder(
            sink = System.err,
            prefixFormatter = prefixFormatter,
        )

        val startTime = System.currentTimeMillis()

        val result: GradleBuildResult = try {
            connection.use { conn ->
                val launcher = conn.newBuild()
                    .addProgressListener(taskExecutionListener, setOf(OperationType.TASK, OperationType.FILE_DOWNLOAD))
                    .setStandardError(stderrRecorder)
                    .setStandardOutput(stdoutRecorder)
                profiler?.configure(launcher)
                launcher.addArguments(buildArgs)
                launcher.run()
            }
            GradleBuildResult.Success
        } catch(ex: RuntimeException) {
            when(ex) {
                is UnsupportedOperationConfigurationException ->
                    GradleBuildResult.UnsupportedOperationConfiguration(ex.message ?: "[no message]")
                is UnsupportedVersionException ->
                    GradleBuildResult.UnsupportedVersion(ex.message ?: "[no message]")
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
        } finally {
            if (singleUse) ProfilerSession.stopIsolatedDaemon()
        }
        val endTime = System.currentTimeMillis()

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
            stdoutLines = stdoutRecorder.getRecordedLines(),
            stderrLines = stderrRecorder.getRecordedLines(),
        )
        writeRecording(recordPath, events)
        if (result == GradleBuildResult.Success) {
            return
        }
        when(result) {
            is GradleBuildResult.BuildFailure ->
                error("Build failed: ${result.message}")
            is GradleBuildResult.BuildCancelled ->
                error("Build cancelled: ${result.message}")
            is GradleBuildResult.UnsupportedVersion ->
                error("Unsupported Gradle version: ${result.message}")
            is GradleBuildResult.UnsupportedOperationConfiguration ->
                error("Unsupported configuration: ${result.message}")
            is GradleBuildResult.GradleConnectionError ->
                error("Gradle connection error: ${result.message}")
            is GradleBuildResult.UnsupportedBuildArgument ->
                error("Unsupported build argument: ${result.message}")
        }
    }
}

fun main(args: Array<String>) {
    Entrypoint().main(args)
}
