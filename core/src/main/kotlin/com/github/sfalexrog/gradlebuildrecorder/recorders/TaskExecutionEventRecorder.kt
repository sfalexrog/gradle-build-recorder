package com.github.sfalexrog.gradlebuildrecorder.recorders

import com.github.sfalexrog.gradlebuildrecorder.models.TaskCompletionEvent
import com.github.sfalexrog.gradlebuildrecorder.models.TaskExecutionEvent
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.model.UnsupportedMethodException

class TaskExecutionEventRecorder: EventRecorder<TaskFinishEvent, TaskExecutionEvent> {
    private val finishedTasks = mutableListOf<TaskExecutionEvent>()

    override fun processEvent(event: TaskFinishEvent) {
        val taskResult = event.result
        val startTime = taskResult.startTime
        val endTime = taskResult.endTime
        val taskEvent = when(taskResult) {
            is TaskSuccessResult -> {
                TaskCompletionEvent.Success(
                    startTime = startTime,
                    endTime = endTime,
                    isUpToDate = taskResult.isUpToDate,
                    isFromCache = taskResult.isFromCache,
                )
            }
            is TaskSkippedResult -> {
                TaskCompletionEvent.Skipped(
                    startTime = startTime,
                    endTime = endTime,
                    reason = taskResult.skipMessage
                )
            }
            is TaskFailureResult -> {
                TaskCompletionEvent.Failed(
                    startTime = startTime,
                    endTime = endTime,
                    failures = taskResult.failures.map { it.message ?: "unknown" }
                )
            }
            else -> error("unexpected taskResult class: ${taskResult.javaClass}")
        }
        val descriptor = event.descriptor
        val taskInfo = TaskExecutionEvent(
            taskPath = descriptor.taskPath,
            originPlugin = try {
                descriptor.originPlugin?.displayName ?: "unknown/builtin"
            } catch (ex: UnsupportedMethodException) { "unsupported: $ex" },
            dependencies = try {
                descriptor.dependencies.map { it.name }
            } catch (_: UnsupportedMethodException) { listOf() },
            event = taskEvent,
        )
        synchronized(this) {
            finishedTasks.add(taskInfo)
        }
    }

    override fun getRecordedEvents(): List<TaskExecutionEvent> = synchronized(this) {
        finishedTasks
    }
}
