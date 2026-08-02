package com.github.sfalexrog.gradlebuildrecorder.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskExecutionEvent(
    val taskPath: String,
    val originPlugin: String,
    val dependencies: List<String>,
    val event: TaskCompletionEvent,
)

@Serializable
sealed class TaskCompletionEvent {
    abstract val startTime: Long
    abstract val endTime: Long

    @Serializable
    @SerialName("success")
    data class Success(
        override val startTime: Long,
        override val endTime: Long,
        val isUpToDate: Boolean,
        val isFromCache: Boolean,
    ) : TaskCompletionEvent()
    @Serializable
    @SerialName("skipped")
    data class Skipped(
        override val startTime: Long,
        override val endTime: Long,
        val reason: String,
    ) : TaskCompletionEvent()
    @Serializable
    @SerialName("failed")
    data class Failed(
        override val startTime: Long,
        override val endTime: Long,
        val failures: List<String>,
    ) : TaskCompletionEvent()
}