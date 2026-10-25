import type { GradleBuildRecord, Task } from '../recording'
export const task = (path = ':app:compile', start = 100, end = 200): Task => ({
  taskPath: path,
  originPlugin: 'test',
  dependencies: [],
  event: { type: 'success', startTime: start, endTime: end, isUpToDate: false, isFromCache: false },
})
export const fixture: GradleBuildRecord = {
  invocation: {
    projectPath: '/sample',
    gradleArgs: ['build'],
    result: { type: 'success' },
    startTime: 0,
    endTime: 500,
  },
  taskExecutionEvents: [task(), task(':lib:test', 150, 300)],
  fileDownloadEvents: [],
  stdoutLines: [{ startTime: 100, endTime: 100, line: 'hello' }],
  stderrLines: [{ startTime: 250, endTime: 250, line: 'warning' }],
}
