package com.github.sfalexrog.gradlebuildrecorder.models

import kotlinx.serialization.Serializable

@Serializable
data class LogLineEvent(
    val startTime: Long,
    val endTime: Long,
    val line: String,
)
