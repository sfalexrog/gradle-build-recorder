package com.github.sfalexrog.gradlebuildrecorder.internal

import jdk.jfr.consumer.RecordingFile
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class ProfilerSessionTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `extracted artifacts are stable and do not change existing files`() {
        val first = cacheProfilerFile("artifact", byteArrayOf(1, 2, 3))
        val modified = first.lastModified()
        assertEquals(first, cacheProfilerFile("artifact", byteArrayOf(1, 2, 3)))
        assertEquals(modified, first.lastModified())
        assertNotEquals(first, cacheProfilerFile("artifact", byteArrayOf(1, 2, 4)))
    }

    @Test
    fun `rejects output paths and options that interfere with lifecycle control`() {
        assertThrows(IllegalArgumentException::class.java) { ProfilerSession(File("a,b.jfr"), "jfr,all") }
        for (option in listOf("file=a.jfr", "stop", "start", "resume")) {
            assertThrows(IllegalArgumentException::class.java) { ProfilerSession(File("a.jfr"), "jfr,$option") }
        }
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `profiles startup reuse cache hits and failures without leaving profiling active`() {
        File(directory, "settings.gradle").writeText("rootProject.name = 'profiler-test'")
        File(directory, "build.gradle").writeText("""
            tasks.register('probe') {
                doLast {
                    println('DAEMON_PID=' + ProcessHandle.current().pid())
                    Thread.sleep(200)
                }
            }
            tasks.register('fail') { doLast { throw new GradleException('expected failure') } }
        """.trimIndent())
        val registry = File(directory, "daemon-registry")
        val connector = (GradleConnector.newConnector() as DefaultGradleConnector)
            .daemonBaseDir(registry)
            .useInstallation(File(System.getProperty("test.gradle.home")))
            .forProjectDirectory(directory)
        var daemonPid: Long? = null
        try {
            connector.connect().use { connection ->
                fun record(name: String, task: String = "probe", fails: Boolean = false): String {
                    val output = File(directory, "$name.jfr")
                    val log = ByteArrayOutputStream()
                    val launcher = connection.newBuild().forTasks(task)
                        .withArguments("--configuration-cache", "--stacktrace")
                        .setStandardOutput(log).setStandardError(log)
                    ProfilerSession(output, "jfr,all").configure(launcher)
                    if (fails) assertThrows(BuildException::class.java) { launcher.run() } else launcher.run()
                    assertTrue(output.isFile, log.toString())
                    RecordingFile(output.toPath()).use { assertTrue(it.hasMoreEvents(), log.toString()) }
                    return log.toString()
                }
                val first = record("first")
                val pid = Regex("DAEMON_PID=(\\d+)")
                daemonPid = checkNotNull(pid.find(first)).groupValues[1].toLong()
                val second = record("second")
                assertEquals(pid.find(first)?.groupValues?.get(1), pid.find(second)?.groupValues?.get(1))
                assertTrue(second.contains("Reusing configuration cache"), second)
                record("task-failure", "fail", fails = true)
                // Reconfiguration must not install duplicate global listeners.
                File(directory, "build.gradle").appendText("\n// change configuration\n")
                record("reconfigured")
                File(directory, "settings.gradle").appendText("\nthrow new GradleException('settings failure')\n")
                record("settings-failure", fails = true)
                File(directory, "settings.gradle").writeText("rootProject.name = 'profiler-test'")
                record("after-failure")
            }
        } finally {
            // Only this test's private daemon was started by this Tooling API client.
            ConnectorServices.reset()
            daemonPid?.let { pid ->
                ProcessHandle.of(pid).ifPresent { it.onExit().get(30, TimeUnit.SECONDS) }
            }
        }
    }
}
