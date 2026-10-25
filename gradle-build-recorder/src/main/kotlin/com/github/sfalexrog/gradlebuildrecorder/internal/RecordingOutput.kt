package com.github.sfalexrog.gradlebuildrecorder.internal

import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildRecord
import com.github.sfalexrog.gradlebuildrecorder.viewer.RecordingHtml
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.File

/** Writes an offline viewer for a `.html` extension (ignoring case), and JSON otherwise. */
@OptIn(ExperimentalSerializationApi::class)
internal fun writeRecording(destination: File, recording: GradleBuildRecord) {
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    if (destination.extension.equals("html", ignoreCase = true)) {
        destination.writeText(RecordingHtml.render(json.encodeToString(recording)), Charsets.UTF_8)
    } else {
        // Keep JSON output streaming so large recordings do not need an extra serialized copy.
        destination.outputStream().use { json.encodeToStream(recording, it) }
    }
}
