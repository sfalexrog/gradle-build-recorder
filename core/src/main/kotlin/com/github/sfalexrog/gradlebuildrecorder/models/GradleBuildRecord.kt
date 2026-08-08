package com.github.sfalexrog.gradlebuildrecorder.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GradleInvocation(
    val projectPath: String,
    val gradleArgs: List<String>,
    val result: GradleBuildResult,
    val startTime: Long,
    val endTime: Long,
)

@Serializable
sealed class GradleBuildResult {
    @Serializable
    @SerialName("success")
    data object Success: GradleBuildResult()

    @Serializable
    @SerialName("unsupported_gradle_version")
    data class UnsupportedVersion(val message: String): GradleBuildResult()

    @Serializable
    @SerialName("unsupported_operation_configuration")
    data class UnsupportedOperationConfiguration(val message: String): GradleBuildResult()

    @Serializable
    @SerialName("unsupported_build_argument")
    data class UnsupportedBuildArgument(val message: String): GradleBuildResult()

    @Serializable
    @SerialName("build_failure")
    data class BuildFailure(val message: String): GradleBuildResult()

    @Serializable
    @SerialName("build_cancelled")
    data class BuildCancelled(val message: String): GradleBuildResult()

    @Serializable
    @SerialName("gradle_connection_error")
    data class GradleConnectionError(val message: String): GradleBuildResult()

}

@Serializable
data class GradleBuildRecord(
    val invocation: GradleInvocation,
    val fileDownloadEvents: List<FileDownloadEvent>,
    val taskExecutionEvents: List<TaskExecutionEvent>,
)
