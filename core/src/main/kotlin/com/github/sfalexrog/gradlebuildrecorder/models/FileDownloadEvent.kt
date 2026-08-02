package com.github.sfalexrog.gradlebuildrecorder.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileDownloadEvent(
    val startTime: Long,
    val endTime: Long,
    val uri: String,
    val bytesDownloaded: Long,
    val result: FileDownloadResult,
)

@Serializable
sealed class FileDownloadResult {

    @Serializable
    @SerialName("success")
    data object Success: FileDownloadResult()

    @Serializable
    @SerialName("failure")
    data class Failure(
        val messages: List<String>,
    ): FileDownloadResult()

    @Serializable
    @SerialName("not_found")
    data object NotFound: FileDownloadResult()
}
