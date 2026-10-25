package com.github.sfalexrog.gradlebuildrecorder.internal

import jdk.jfr.consumer.RecordingFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

@EnabledOnOs(OS.MAC, OS.LINUX)
class SingleUseDaemonTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `single use builds always start fresh and shut down on success and failure`() {
        File(directory, "settings.gradle").writeText("rootProject.name = 'single-use-test'")
        File(directory, "build.gradle").writeText("""
            tasks.register('probe') {
                doLast { println('DAEMON_PID=' + ProcessHandle.current().pid()) }
            }
            tasks.register('fail') {
                doLast {
                    println('DAEMON_PID=' + ProcessHandle.current().pid())
                    throw new GradleException('expected failure')
                }
            }
        """.trimIndent())
        val wrapper = File(directory, "gradle/wrapper/gradle-wrapper.properties")
        wrapper.parentFile.mkdirs()
        File(System.getProperty("test.wrapper.properties")).copyTo(wrapper)
        withPreservedDaemon { keeperPid ->
            var previousPid: Long? = null
            for (task in listOf("probe", "fail")) {
                val log = File(directory, "$task.log")
                val profile = File(directory, "$task-%p.jfr")
                val command = listOf(
                    File(System.getProperty("java.home"), "bin/java").absolutePath,
                    "-jar", System.getProperty("test.recorder.jar"),
                    "-p", "true", "--profiler-output", profile.absolutePath,
                    "-o", File(directory, "$task.json").absolutePath,
                ) + (if (task == "probe") listOf("--no-daemon") else emptyList()) +
                    listOf(directory.absolutePath, "--", task) +
                    (if (task == "fail") listOf("--no-daemon") else emptyList())
                val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
                try {
                    assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Recorder timed out: ${log.readText()}")
                    val text = log.readText()
                    if (task == "probe") assertEquals(0, process.exitValue(), text)
                    else assertNotEquals(0, process.exitValue(), text)
                    val pid = checkNotNull(Regex("DAEMON_PID=(\\d+)").find(text)) { text }.groupValues[1].toLong()
                    assertNotEquals(previousPid, pid)
                    previousPid = pid
                    assertNotEquals(keeperPid, pid)
                    assertTrue(ProcessHandle.of(keeperPid).map { it.isAlive }.orElse(false), text)
                    ProcessHandle.of(pid).ifPresent { it.onExit().get(30, TimeUnit.SECONDS) }
                    assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), text)
                    val recording = File(profile.absolutePath.replace("%p", pid.toString()))
                    RecordingFile(recording.toPath()).use { assertTrue(it.hasMoreEvents(), text) }
                    assertTrue(File(directory, "$task.json").isFile)
                } finally {
                    if (process.isAlive) process.destroyForcibly()
                }
            }
        }
    }

    /** Keep an unrelated, privately registered profiled daemon alive while exercising the CLI. */
    private fun withPreservedDaemon(action: (Long) -> Unit) {
        val connector = (GradleConnector.newConnector() as DefaultGradleConnector)
            .daemonBaseDir(File(directory, "keeper-registry"))
            .useInstallation(File(System.getProperty("test.gradle.home")))
            .forProjectDirectory(directory)
        var pid: Long? = null
        try {
            connector.connect().use { connection ->
                val log = ByteArrayOutputStream()
                val launcher = connection.newBuild().forTasks("probe").setStandardOutput(log)
                ProfilerSession(File(directory, "keeper.jfr"), "jfr,all").configure(launcher)
                launcher.run()
                val keeperPid = checkNotNull(Regex("DAEMON_PID=(\\d+)").find(log.toString()))
                    .groupValues[1].toLong()
                pid = keeperPid
                action(keeperPid)
            }
        } finally {
            ConnectorServices.reset()
            pid?.let { daemon -> ProcessHandle.of(daemon).ifPresent { it.onExit().get(30, TimeUnit.SECONDS) } }
        }
    }
}
