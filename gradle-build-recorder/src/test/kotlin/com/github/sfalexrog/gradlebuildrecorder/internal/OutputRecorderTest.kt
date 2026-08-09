package com.github.sfalexrog.gradlebuildrecorder.internal

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class OutputRecorderTest {
    @Test
    fun `utf-8 sequences are stored and parsed correctly`() {
        val testSink = ByteArrayOutputStream()
        var testTimestamp: Long = 69
        val recorder = OutputRecorder(
            sink = testSink,
            timestamper = { testTimestamp },
            prefixFormatter = { ts -> "[$ts] " }
        )
        listOf(0xf0, 0x9f, 0x90, 0x82, 0xf0, 0x9f, 0x92, 0xa9).forEach { code -> recorder.write(code) }
        recorder.flush()
        assert(testSink.toString(Charsets.UTF_8) == "[69] \uD83D\uDC02\uD83D\uDCA9")
    }

    @Test
    fun `flush after write respects newlines`() {
        val testSink = ByteArrayOutputStream()
        var testTimestamp: Long = 0
        val recorder = OutputRecorder(
            sink=testSink,
            timestamper = { testTimestamp },
            prefixFormatter = { ts -> "[$ts] " }
        )
        listOf('H', 'e', 'l').forEach { recorder.write(it.code) }
        recorder.flush()
        assert(testSink.toString(Charsets.UTF_8) == "[0] Hel") {
            "${testSink.toString(Charsets.UTF_8)} != \"[0] Hel\""
        }
        listOf('l', 'o').forEach { recorder.write(it.code) }
        recorder.flush()
        assert(testSink.toString(Charsets.UTF_8) == "[0] Hello") { testSink.toString(Charsets.UTF_8) }
        testTimestamp = 10
        recorder.write(", World!\nWhat a ".toByteArray(Charsets.UTF_8))
        recorder.flush()
        assert(testSink.toString(Charsets.UTF_8) == "[0] Hello, World!\n[10] What a ") { testSink.toString(Charsets.UTF_8) }

        val lines = recorder.getRecordedLines()
        assert(lines.size == 1) { lines.size }
        assert(lines[0].startTime == 0.toLong()) { "start timestamp ${lines[0].startTime}" }
        assert(lines[0].endTime == 10.toLong()) { "end timestamp ${lines[0].endTime}" }
        assert(lines[0].line == "Hello, World!") { "line ${lines[0].line}" }

        recorder.close()
        val lines2 = recorder.getRecordedLines()
        assert(lines2.size == 2) { lines.size }
        assert(lines[1].line == "What a ") { "line ${lines[1].line}" }
    }

    @Test
    fun `multiple lines within one write work as expected`() {
        val testSink = ByteArrayOutputStream()
        val testTimestamp = 10.toLong()
        val recorder = OutputRecorder(
            sink = testSink,
            timestamper = { testTimestamp },
            prefixFormatter = { ts -> "[$ts] " },
        )
        recorder.write("We should support\nhandling multiple lines\nin a single write call".toByteArray(Charsets.UTF_8))
        recorder.flush()
        assert(testSink.toString(Charsets.UTF_8) == "[10] We should support\n[10] handling multiple lines\n[10] in a single write call") { testSink.toString(Charsets.UTF_8) }
    }
}
