package com.github.sfalexrog.gradlebuildrecorder.viewer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Creates standalone, offline HTML reports using the viewer bundled in this module's resources. */
object RecordingHtml {
    private const val EMPTY_RECORDING = """<script id="gbr-recording" type="application/json">""</script>"""

    /**
     * Returns the viewer with [recordingJson] embedded, or the empty file picker when omitted.
     *
     * Pass the recorder's serialized JSON directly. Validation is performed by the viewer on load.
     * The JSON is encoded as a string and HTML-sensitive characters are escaped so arbitrary log
     * output (including script tags) remains data. The resource template is read afresh for each call.
     */
    fun render(recordingJson: String = ""): String {
        val template = checkNotNull(RecordingHtml::class.java.getResourceAsStream("/recording-viewer.html")) {
            "Bundled recording viewer resource is missing"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val position = template.indexOf(EMPTY_RECORDING)
        check(position >= 0 && template.indexOf(EMPTY_RECORDING, position + EMPTY_RECORDING.length) < 0) {
            "Bundled recording viewer must contain exactly one empty recording slot"
        }
        // Escaping '<' prevents the HTML parser from interpreting closing script tags or comments.
        val encoded = Json.encodeToString(recordingJson).replace("<", "\\u003c")
        val slot = EMPTY_RECORDING.replace("\"\"</script>", "$encoded</script>")
        return template.replaceRange(position, position + EMPTY_RECORDING.length, slot)
    }
}
