package com.github.sfalexrog.gradlebuildrecorder.internal

import com.github.sfalexrog.gradlebuildrecorder.models.LogLineEvent
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * Output recorder.
 *
 * Attempts to record lines to its internal buffer, while extending the output
 * with timestamps (optionally).
 */
internal class OutputRecorder(
    val sink: OutputStream = System.out,
    val timestamper: () -> Long = { System.currentTimeMillis() },
    val prefixFormatter: (Long) -> String = { "" },
): OutputStream() {
    private val preFlushBuffer = ByteArrayOutputStream()
    private val currentLineBuffer = ByteArrayOutputStream()
    private var currentLineBufferOffset = 0
    private var currentLineTimeStart: Long = 0
    private val lineRecorder = LineRecorder()

    override fun write(charByte: Int) {
        if ((preFlushBuffer.size() == 0) and (currentLineBuffer.size() == 0)) {
            currentLineTimeStart = timestamper()
        }
        preFlushBuffer.write(charByte)
    }

    override fun write(encodedBuffer: ByteArray, start: Int, end: Int) {
        if ((preFlushBuffer.size() == 0) and (currentLineBuffer.size() == 0)) {
            currentLineTimeStart = timestamper()
        }
        preFlushBuffer.write(encodedBuffer, start, end)
    }

    override fun flush() {
        processPreFlushBuffer()
        super.flush()
    }

    override fun close() {
        flush()
        flushCurrentLineBuffer()
    }

    fun processPreFlushBuffer() {
        val preFlushBytes = preFlushBuffer.toByteArray()
        var lineStartPos = 0
        val now: Long = timestamper()
        for(bufferPos in 0.until(preFlushBytes.size)) {
            if (preFlushBytes[bufferPos] == '\n'.code.toByte()) {
                if (currentLineBuffer.size() > 0) {
                    currentLineBuffer.write(preFlushBytes, lineStartPos, bufferPos - lineStartPos)
                    flushCurrentLineBuffer()
                    currentLineTimeStart = now
                } else {
                    sink.write(prefixFormatter(now).toByteArray(Charset.defaultCharset()))
                    sink.write(preFlushBytes, lineStartPos, bufferPos - lineStartPos)
                    sink.write('\n'.code)
                }
                lineStartPos = bufferPos + 1
            }
        }
        if (lineStartPos < preFlushBytes.size) {
            if (currentLineBuffer.size() == 0) {
                sink.write(prefixFormatter(now).toByteArray(Charset.defaultCharset()))
            }
            currentLineBuffer.write(preFlushBytes, lineStartPos, preFlushBytes.size - lineStartPos)
            currentLineBufferOffset += preFlushBytes.size - lineStartPos
            sink.write(preFlushBytes, lineStartPos, preFlushBytes.size - lineStartPos)
        }
        preFlushBuffer.reset()
    }

    fun flushCurrentLineBuffer() {
        if (currentLineBuffer.size() == 0) return
        val endTime = timestamper()
        lineRecorder.add(
            LogLineEvent(
                startTime = currentLineTimeStart,
                endTime = endTime,
                line = currentLineBuffer.toString(Charset.defaultCharset())
            )
        )
        sink.write(currentLineBuffer.toByteArray(), currentLineBufferOffset, currentLineBuffer.size() - currentLineBufferOffset)
        sink.write('\n'.code)
        currentLineBuffer.reset()
        currentLineBufferOffset = 0
    }

    fun getRecordedLines() = lineRecorder.getLines()
}
