package com.github.sfalexrog.gradlebuildrecorder.internal

import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import java.io.File
import java.nio.file.Files

/** Configures native startup profiling and a daemon-side listener for subsequent recordings. */
internal class ProfilerSession(output: File, private val arguments: String) {
    private val outputPath = output.absolutePath

    init {
        require(',' !in outputPath) { "Profiler output paths cannot contain commas" }
        require(arguments.split(',').none { it.substringBefore('=') in RESERVED_OPTIONS }) {
            "Profiler arguments must contain recording options only; start/stop and file= are managed by the recorder"
        }
    }

    fun configure(launcher: ConfigurableLauncher<*>) {
        val library = extractAsyncProfiler()
        val api = extractProfilerApi()
        val staging = File(library.parentFile, "recording-%p.jfr").absolutePath
        require(',' !in staging) { "Profiler cache paths cannot contain commas" }
        val script = checkNotNull(javaClass.getResourceAsStream("/profiler.init.gradle"))
            .bufferedReader().use { it.readText() }
            .replace("@STAGING@", staging.replace("\\", "\\\\").replace("'", "\\'"))
            .replace("@PROFILER_API@", api.absolutePath.replace("\\", "\\\\").replace("'", "\\'"))
        val initScript = cacheProfilerFile("profiler.init.gradle", script.toByteArray())
        // No per-invocation path in JVM arguments: changing it would require a new daemon.
        launcher.addJvmArguments("-agentpath:${library.absolutePath}=start,$arguments,file=$staging")
        launcher.addArguments("--init-script", initScript.absolutePath)
        val properties = System.getProperties().stringPropertyNames().associateWith { System.getProperty(it) }
        launcher.withSystemProperties(properties + mapOf(
            "gbr.profiler.output" to outputPath,
            "gbr.profiler.args" to arguments,
        ))
    }

    companion object {
        private val RESERVED_OPTIONS = setOf(
            "file", "start", "restart", "resume", "stop", "dump", "status", "version", "check", "list",
            "timeout", "loop", "begin", "end", "nostop",
        )

        /** Tooling API always uses daemons; a private registry prevents reusing or stopping other builds' daemons. */
        fun isolateDaemon(connector: GradleConnector) {
            val registry = Files.createTempDirectory("gbr-daemon-").toFile()
            (connector as DefaultGradleConnector).daemonBaseDir(registry)
        }

        /** Requests shutdown of this Tooling API client's daemons, including after a failed build. */
        fun stopIsolatedDaemon() {
            DefaultGradleConnector.close()
        }
    }
}
