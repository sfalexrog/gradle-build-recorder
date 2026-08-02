package com.github.sfalexrog.gradlebuildrecorder.recorders

import com.github.sfalexrog.gradlebuildrecorder.models.FileDownloadEvent
import com.github.sfalexrog.gradlebuildrecorder.models.FileDownloadResult
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.download.FileDownloadFinishEvent
import org.gradle.tooling.events.download.FileDownloadNotFoundResult

class FileDownloadEventRecorder: EventRecorder<FileDownloadFinishEvent, FileDownloadEvent> {
    private val finishedDownloads = mutableListOf<FileDownloadEvent>()

    override fun processEvent(event: FileDownloadFinishEvent) {
        val eventResult = event.result
        val result = when(eventResult) {
            is FileDownloadNotFoundResult -> FileDownloadResult.NotFound
            is FailureResult -> FileDownloadResult.Failure(messages = eventResult.failures.map { it.message ?: "[no message]" })
            is SuccessResult -> FileDownloadResult.Success
            else -> error("Unexpected result class: ${event.result.javaClass}")
        }
        finishedDownloads.add(
            FileDownloadEvent(
                startTime = event.result.startTime,
                endTime = event.result.endTime,
                uri = event.descriptor.uri.toString(),
                bytesDownloaded = event.result.bytesDownloaded,
                result = result,
            )
        )
    }

    override fun getRecordedEvents(): List<FileDownloadEvent> = synchronized(this) {
        finishedDownloads
    }
}