package com.github.sfalexrog.gradlebuildrecorder.viewer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingHtmlTest {
    private val openingTag = """<script id="gbr-recording" type="application/json">"""

    private fun embeddedJson(html: String): String = Json.parseToJsonElement(
        html.substringAfter(openingTag).substringBefore("</script>")
    ).jsonPrimitive.content

    @Test
    fun `packaged viewer contains exactly one empty slot and inlined app`() {
        val html = RecordingHtml.render()
        assertTrue(html.startsWith("<!doctype html>", ignoreCase = true))
        assertEquals(2, html.split(openingTag).size)
        assertEquals("", embeddedJson(html))
        assertFalse(html.contains("src=\"/src/main.ts\""))
        assertTrue(html.contains("<script type=\"module\""))
    }

    @Test
    fun `embedding preserves pretty printed JSON and unicode`() {
        val json = """
            {
              "line": "quotes: \" and backslash: \\ and newline: \n and Unicode: Հայերեն 😀"
            }
        """.trimIndent()
        assertEquals(json, embeddedJson(RecordingHtml.render(json)))
    }

    @Test
    fun `HTML and script content cannot escape the data slot`() {
        val json = """{"line":"</ScRiPt><script>alert('x')</script><!-- & < > \""}"""
        val html = RecordingHtml.render(json)
        val slot = html.substringAfter(openingTag).substringBefore("</script>")
        assertFalse(slot.contains('<'))
        assertEquals(json, embeddedJson(html))
    }

    @Test
    fun `rendering leaves the template available for later recordings`() {
        val first = RecordingHtml.render("{\"first\":true}")
        val second = RecordingHtml.render("{\"second\":true}")
        assertEquals("{\"first\":true}", embeddedJson(first))
        assertEquals("{\"second\":true}", embeddedJson(second))
        assertEquals("", embeddedJson(RecordingHtml.render()))
    }
}
