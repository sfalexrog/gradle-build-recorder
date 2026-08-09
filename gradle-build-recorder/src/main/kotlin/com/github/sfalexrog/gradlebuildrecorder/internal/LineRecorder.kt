package com.github.sfalexrog.gradlebuildrecorder.internal

import com.github.sfalexrog.gradlebuildrecorder.models.LogLineEvent

class LineRecorder {
    val recordedLines = mutableListOf<LogLineEvent>()

    fun add(lineEntry: LogLineEvent) {
        recordedLines.add(lineEntry)
    }

    fun getLines() = recordedLines
}
