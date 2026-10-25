package com.github.sfalexrog.gradlebuildrecorder.internal

import one.profiler.AsyncProfiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** Immutable, content-addressed files survive recorder exits so Gradle can reuse a profiled daemon. */
internal fun cacheProfilerFile(name: String, bytes: ByteArray): File {
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val directory = File(System.getProperty("java.io.tmpdir"), "gradle-build-recorder/$hash")
    Files.createDirectories(directory.toPath())
    val target = File(directory, name)
    if (!target.exists()) {
        val temporary = Files.createTempFile(directory.toPath(), name, ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
    return target
}

/** Extracts the native agent without loading it into the recorder's JVM. */
fun extractAsyncProfiler(): File {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val platform = when {
        os.contains("mac") -> "macos"
        os.contains("linux") && arch in setOf("amd64", "x86_64", "x64") -> "linux-x64"
        os.contains("linux") && arch in setOf("aarch64", "arm64") -> "linux-arm64"
        else -> error("async-profiler is not supported on $os/$arch")
    }
    val bytes = checkNotNull(AsyncProfiler::class.java.getResourceAsStream("/$platform/libasyncProfiler.so")) {
        "Missing async-profiler native library for $platform"
    }.use { it.readBytes() }
    return cacheProfilerFile("libasyncProfiler.so", bytes)
}

/** A small Java API jar for the init script; works with both normal and shaded recorder distributions. */
internal fun extractProfilerApi(): File {
    val output = ByteArrayOutputStream()
    JarOutputStream(output).use { jar ->
        for (name in listOf("AsyncProfiler", "AsyncProfilerMXBean", "Counter")) {
            val resource = "one/profiler/$name.class"
            jar.putNextEntry(JarEntry(resource).apply { time = 0 })
            checkNotNull(AsyncProfiler::class.java.getResourceAsStream("/$resource"))
                .use { it.copyTo(jar) }
            jar.closeEntry()
        }
    }
    return cacheProfilerFile("profiler-api.jar", output.toByteArray())
}
