package com.github.sfalexrog.gradlebuildrecorder.internal

import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildRecord
import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildResult
import com.github.sfalexrog.gradlebuildrecorder.models.GradleInvocation
import com.github.sfalexrog.gradlebuildrecorder.models.LogLineEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class RecordingOutputTest {
    @TempDir
    lateinit var directory: File

    private val recording = GradleBuildRecord(
        invocation = GradleInvocation("/example", listOf("build"), GradleBuildResult.BuildFailure("failed"), 0, 10),
        fileDownloadEvents = emptyList(),
        taskExecutionEvents = emptyList(),
        stdoutLines = listOf(LogLineEvent(1, 2, "</script><script>alert(\"hello\")</script> Հայերեն 😀\n")),
        stderrLines = emptyList(),
    )

    @ParameterizedTest
    @ValueSource(strings = ["build.html", "build.HTML", "build.HtMl"])
    fun `html extension writes the viewer with the complete recording`(name: String) {
        val destination = File(directory, name)
        writeRecording(destination, recording)
        val html = destination.readText(Charsets.UTF_8)
        assertTrue(html.startsWith("<!doctype html>", ignoreCase = true))
        val slot = html.substringAfter("""<script id="gbr-recording" type="application/json">""")
            .substringBefore("</script>")
        assertFalse(slot.contains('<'))
        val embeddedJson = Json.parseToJsonElement(slot).jsonPrimitive.content
        assertEquals(recording, Json.decodeFromString<GradleBuildRecord>(embeddedJson))
    }

    @ParameterizedTest
    @ValueSource(strings = ["build.json", "build.JSON", "build.txt", "build", "build.html.json"])
    fun `other extensions retain JSON output`(name: String) {
        val destination = File(directory, name)
        writeRecording(destination, recording)
        val json = destination.readText(Charsets.UTF_8)
        assertEquals(recording, Json.decodeFromString<GradleBuildRecord>(json))
        assertTrue(json.contains("\n  \"invocation\""), "JSON remains pretty printed")
    }
}
