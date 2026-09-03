package com.github.sfalexrog.gradlebuildrecorder.internal

import one.profiler.AsyncProfiler
import java.io.File

private fun asyncProfilerPlatformDirectory(): String? {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    if (os.contains("linux")) {
        if (arrayOf("amd64", "x86_64", "x64").contains(arch)) {
            return "linux-x64"
        }
        if (arrayOf("aarch64", "arm64").contains(arch)) {
            return "linux-arm64"
        }
    } else if (os.contains("mac")) {
        return "macos"
    }
    return null
}

fun extractAsyncProfiler(): File? {
    val platformDir = asyncProfilerPlatformDirectory() ?: return null
    val asyncProfilerPath = "/$platformDir/libasyncProfiler.so"
    val asyncProfilerStream = AsyncProfiler::class.java.getResourceAsStream(asyncProfilerPath) ?: return null

    val extractPath: String? = System.getProperty("one.profiler.extractPath")
    val extractedFile = if (extractPath != null) {
        File(extractPath, "libasyncProfiler.so")
    } else {
        File("libasyncProfiler.so")
    }
//    val extractedFile = File.createTempFile("libasyncProfiler", ".so",
//        extractPath?.let { File(it) })
    extractedFile.outputStream().use { os ->
        asyncProfilerStream.copyTo(os)
    }
    AsyncProfiler.getInstance()
    return extractedFile
}
