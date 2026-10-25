/* Generated from core/schemas/GradleBuildRecord.json. Run npm run schema:types. */

export type InvocationComGithubSfalexrogGradlebuildrecorderModelsGradleBuildResult =
  | InvocationBuildCancelled
  | InvocationBuildFailure
  | InvocationGradleConnectionError
  | InvocationSuccess
  | InvocationUnsupportedBuildArgument
  | InvocationUnsupportedGradleVersion
  | InvocationUnsupportedOperationConfiguration
export type DownloadComGithubSfalexrogGradlebuildrecorderModelsFileDownloadResult =
  DownloadFailure | DownloadNotFound | DownloadSuccess
export type TaskComGithubSfalexrogGradlebuildrecorderModelsTaskCompletionEvent = TaskFailed | TaskSkipped | TaskSuccess

export interface GradleBuildRecord {
  invocation: {
    projectPath: string
    gradleArgs: string[]
    result: InvocationComGithubSfalexrogGradlebuildrecorderModelsGradleBuildResult
    startTime: number
    endTime: number
  }
  fileDownloadEvents: {
    startTime: number
    endTime: number
    uri: string
    bytesDownloaded: number
    result: DownloadComGithubSfalexrogGradlebuildrecorderModelsFileDownloadResult
  }[]
  taskExecutionEvents: {
    taskPath: string
    originPlugin: string
    dependencies: string[]
    event: TaskComGithubSfalexrogGradlebuildrecorderModelsTaskCompletionEvent
  }[]
  stdoutLines: {
    startTime: number
    endTime: number
    line: string
  }[]
  stderrLines: {
    startTime: number
    endTime: number
    line: string
  }[]
}
export interface InvocationBuildCancelled {
  type: 'build_cancelled'
  message: string
}
export interface InvocationBuildFailure {
  type: 'build_failure'
  message: string
}
export interface InvocationGradleConnectionError {
  type: 'gradle_connection_error'
  message: string
}
export interface InvocationSuccess {
  type: 'success'
}
export interface InvocationUnsupportedBuildArgument {
  type: 'unsupported_build_argument'
  message: string
}
export interface InvocationUnsupportedGradleVersion {
  type: 'unsupported_gradle_version'
  message: string
}
export interface InvocationUnsupportedOperationConfiguration {
  type: 'unsupported_operation_configuration'
  message: string
}
export interface DownloadFailure {
  type: 'failure'
  messages: string[]
}
export interface DownloadNotFound {
  type: 'not_found'
}
export interface DownloadSuccess {
  type: 'success'
}
export interface TaskFailed {
  type: 'failed'
  startTime: number
  endTime: number
  failures: string[]
}
export interface TaskSkipped {
  type: 'skipped'
  startTime: number
  endTime: number
  reason: string
}
export interface TaskSuccess {
  type: 'success'
  startTime: number
  endTime: number
  isUpToDate: boolean
  isFromCache: boolean
}
