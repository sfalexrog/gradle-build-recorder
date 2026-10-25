import Ajv2020 from 'ajv/dist/2020'
import schema from '../../../../core/schemas/GradleBuildRecord.json'
import type { GradleBuildRecord } from './generated/recording'
export type { GradleBuildRecord }
export type Task = GradleBuildRecord['taskExecutionEvents'][number]
export type Download = GradleBuildRecord['fileDownloadEvents'][number]
export type TaskGroup = ReturnType<typeof groupTasks>[number]
export type Range = { start: number; end: number }
const validate = new Ajv2020({ allErrors: false }).compile<GradleBuildRecord>(schema)

/** Validate before using timestamps in SVG geometry. All times are UTC epoch milliseconds. */
export function parseRecording(text: string): GradleBuildRecord {
  const value: unknown = JSON.parse(text)
  if (!validate(value)) {
    const error = validate.errors?.[0]
    throw new Error(`Invalid recording at ${error?.instancePath || '/'}: ${error?.message}`)
  }
  const intervals = [
    value.invocation,
    ...value.taskExecutionEvents.map((t) => t.event),
    ...value.fileDownloadEvents,
    ...value.stdoutLines,
    ...value.stderrLines,
  ]
  if (
    intervals.some(
      (t) =>
        !Number.isSafeInteger(t.startTime) ||
        !Number.isSafeInteger(t.endTime) ||
        t.startTime < 0 ||
        t.endTime < t.startTime ||
        !Number.isFinite(new Date(t.endTime).getTime()),
    )
  ) {
    throw new Error('Recording contains an invalid timestamp or reversed interval.')
  }
  return value
}
export const projectOf = (task: Task) =>
  task.taskPath.slice(0, task.taskPath.lastIndexOf(':')) || ':'
export const duration = (ms: number) =>
  ms < 1000
    ? `${Math.round(ms)} ms`
    : ms < 60000
      ? `${(ms / 1000).toFixed(ms < 10000 ? 2 : 1)} s`
      : `${Math.floor(ms / 60000)}m ${((ms % 60000) / 1000).toFixed(1)}s`

/** Half-open intervals avoid counting tasks that finish exactly when a selection starts. */
export const overlaps = (interval: { startTime: number; endTime: number }, range: Range) =>
  interval.startTime === interval.endTime
    ? interval.startTime >= range.start && interval.startTime < range.end
    : interval.startTime < range.end && interval.endTime > range.start

export function bounds(record: GradleBuildRecord): Range {
  const intervals = [
    record.invocation,
    ...record.taskExecutionEvents.map((t) => t.event),
    ...record.stdoutLines,
    ...record.stderrLines,
    ...record.fileDownloadEvents,
  ]
  let start = Infinity,
    end = -Infinity
  for (const interval of intervals) {
    start = Math.min(start, interval.startTime)
    end = Math.max(end, interval.endTime)
  }
  return { start, end: Math.max(start + 1, end) }
}
export const status = (task: Task) =>
  task.event.type === 'success'
    ? task.event.isFromCache
      ? 'cached'
      : task.event.isUpToDate
        ? 'up-to-date'
        : 'executed'
    : task.event.type

/** A sweep line aggregates simultaneous boundaries before emitting the next step. */
export function parallelism(tasks: Task[], range: Range) {
  const changes = new Map<number, number>([
    [range.start, 0],
    [range.end, 0],
  ])
  for (const task of tasks) {
    if (status(task) !== 'executed' && status(task) !== 'failed') continue
    const start = Math.max(range.start, task.event.startTime)
    const end = Math.min(range.end, task.event.endTime)
    if (end <= start) continue
    changes.set(start, (changes.get(start) || 0) + 1)
    changes.set(end, (changes.get(end) || 0) - 1)
  }
  let count = 0
  return [...changes]
    .sort(([a], [b]) => a - b)
    .map(([time, delta]) => ({ time, count: (count += delta) }))
}

/** Greedy interval packing shared by task and download rows. */
export function assignLanes<T>(
  items: T[],
  interval: (item: T) => { startTime: number; endTime: number },
) {
  const ends: number[] = []
  return [...items]
    .sort((a, b) => interval(a).startTime - interval(b).startTime)
    .map((item) => {
      const times = interval(item)
      let lane = ends.findIndex((end) => end <= times.startTime)
      if (lane === -1) lane = ends.length
      ends[lane] = times.endTime
      return { item, lane }
    })
}

/** Resolve direct dependencies to their recorded bars, preferring the latest preceding execution. */
export function taskDependencies(groups: TaskGroup[], selected: Task | null) {
  const positions = groups.flatMap((group) =>
    group.bars.map((bar) => ({
      task: bar.task,
      y: 117.5 + group.top + bar.lane * 22,
    })),
  )
  const target = positions.find((position) => position.task === selected)
  if (!selected || !target) return []
  return selected.dependencies.flatMap((path) => {
    const candidates = positions.filter(
      (position) => position.task.taskPath === path && position.task !== selected,
    )
    const preceding = candidates
      .filter((position) => position.task.event.endTime <= selected.event.startTime)
      .sort((a, b) => b.task.event.endTime - a.task.event.endTime)
    const source = preceding[0] || candidates[0]
    return source ? [{ source, target }] : []
  })
}

/** Assign overlapping tasks separate lanes while keeping each project in a single group. */
export function groupTasks(tasks: Task[]) {
  const groups = new Map<string, Task[]>()
  for (const task of tasks) {
    const project = projectOf(task)
    if (!groups.has(project)) groups.set(project, [])
    groups.get(project)!.push(task)
  }
  let top = 0
  return [...groups]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, tasks]) => {
      const bars = assignLanes(tasks, (task) => task.event).map(({ item: task, lane }) => ({
        task,
        lane,
      }))
      const height = Math.max(
        48,
        bars.reduce((lanes, bar) => Math.max(lanes, bar.lane + 1), 0) * 22 + 18,
      )
      const group = { name, tasks, bars, top, height }
      top += height
      return group
    })
}
export function clampView(view: Range, full: Range): Range {
  const span = Math.min(full.end - full.start, Math.max(1, view.end - view.start))
  const start = Math.max(full.start, Math.min(view.start, full.end - span))
  return { start, end: start + span }
}
export function ticks(view: Range): number[] {
  const target = (view.end - view.start) / 7
  const power = 10 ** Math.floor(Math.log10(target))
  const step = ([1, 2, 5, 10].find((n) => n * power >= target) || 10) * power
  const values: number[] = []
  for (let t = Math.ceil(view.start / step) * step; t <= view.end; t += step) values.push(t)
  return values
}

/** Union of download lifetimes: concurrent transfers count once toward wall-clock download time. */
export function downloadActivity(downloads: Download[], range: Range): Range[] {
  const intervals = downloads
    .map((item) => ({
      start: Math.max(range.start, item.startTime),
      end: Math.min(range.end, item.endTime),
    }))
    .filter((item) => item.end > item.start)
    .sort((a, b) => a.start - b.start)
  const merged: Range[] = []
  for (const interval of intervals) {
    const last = merged[merged.length - 1]
    if (last && interval.start <= last.end) last.end = Math.max(last.end, interval.end)
    else merged.push({ ...interval })
  }
  return merged
}

/** Instant lookup uses [start, end); instantaneous events intersect only at their exact timestamp. */
export function intersectsTime(interval: { startTime: number; endTime: number }, time: number) {
  return interval.startTime === interval.endTime
    ? time === interval.startTime
    : interval.startTime <= time && time < interval.endTime
}

/** Read the sweep-line value after all boundaries at this timestamp have been applied. */
export function parallelCountAt(points: { time: number; count: number }[], time: number) {
  let low = 0,
    high = points.length
  while (low < high) {
    const mid = Math.floor((low + high) / 2)
    if (points[mid]!.time <= time) low = mid + 1
    else high = mid
  }
  return points[low - 1]?.count ?? 0
}
