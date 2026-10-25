import { describe, expect, it } from 'vitest'
import {
  bounds,
  clampView,
  groupTasks,
  overlaps,
  parallelism,
  parseRecording,
  projectOf,
  ticks,
} from '../recording'
import { fixture, task } from './fixtures'
describe('recording validation and shared schema', () => {
  it('preserves the different success variants without conflating their fields', () => {
    expect(parseRecording(JSON.stringify(fixture))).toEqual(fixture)
    const broken = structuredClone(fixture) as unknown as {
      taskExecutionEvents: { event: object }[]
    }
    broken.taskExecutionEvents[0]!.event = { type: 'success' }
    expect(() => parseRecording(JSON.stringify(broken))).toThrow('Invalid recording')
  })
  it('validates download success, skipped, failed and unsuccessful builds', () => {
    const record = structuredClone(fixture)
    record.invocation.result = { type: 'build_failure', message: 'failed' }
    record.fileDownloadEvents = [
      {
        startTime: 1,
        endTime: 2,
        uri: 'https://example.org',
        bytesDownloaded: 1,
        result: { type: 'success' },
      },
    ]
    record.taskExecutionEvents = [
      { ...task(), event: { type: 'failed', startTime: 1, endTime: 2, failures: ['oops'] } },
      { ...task(), event: { type: 'skipped', startTime: 2, endTime: 2, reason: 'no source' } },
    ]
    expect(parseRecording(JSON.stringify(record))).toEqual(record)
  })
  it('rejects malformed JSON, missing fields, reversed intervals and unsafe timestamps', () => {
    for (const value of [
      '{',
      '{}',
      JSON.stringify({ ...fixture, invocation: { ...fixture.invocation, endTime: -1 } }),
      JSON.stringify({
        ...fixture,
        stdoutLines: [{ startTime: 2 ** 54, endTime: 2 ** 54, line: '' }],
      }),
    ]) {
      expect(() => parseRecording(value)).toThrow(/./)
    }
  })
})
describe('timeline math', () => {
  it('uses half-open overlap and includes instantaneous events', () => {
    const range = { start: 100, end: 200 }
    expect(overlaps({ startTime: 0, endTime: 100 }, range)).toBe(false)
    expect(overlaps({ startTime: 200, endTime: 250 }, range)).toBe(false)
    expect(overlaps({ startTime: 90, endTime: 110 }, range)).toBe(true)
    expect(overlaps({ startTime: 100, endTime: 100 }, range)).toBe(true)
    expect(overlaps({ startTime: 200, endTime: 200 }, range)).toBe(false)
  })
  it('aggregates concurrent boundaries and excludes nonexecuting work', () => {
    const cached = task(':cached', 0, 400)
    if (cached.event.type === 'success') cached.event.isFromCache = true
    expect(
      parallelism(
        [
          task(':a', 100, 200),
          task(':b', 200, 300),
          task(':c', 150, 250),
          cached,
          task(':zero', 100, 100),
        ],
        { start: 0, end: 500 },
      ),
    ).toEqual([
      { time: 0, count: 0 },
      { time: 100, count: 1 },
      { time: 150, count: 2 },
      { time: 200, count: 2 },
      { time: 250, count: 1 },
      { time: 300, count: 0 },
      { time: 500, count: 0 },
    ])
  })
  it('keeps empty and zero-length builds navigable', () => {
    expect(
      bounds({
        ...fixture,
        invocation: { ...fixture.invocation, endTime: 0 },
        taskExecutionEvents: [],
        stdoutLines: [],
        stderrLines: [],
      }),
    ).toEqual({ start: 0, end: 1 })
    expect(ticks({ start: 0, end: 1 }).length).toBeGreaterThan(1)
  })
  it('clamps zoom and pan at build boundaries', () => {
    expect(clampView({ start: -100, end: 100 }, { start: 0, end: 500 })).toEqual({
      start: 0,
      end: 200,
    })
    expect(clampView({ start: 400, end: 600 }, { start: 0, end: 500 })).toEqual({
      start: 300,
      end: 500,
    })
    expect(clampView({ start: -1000, end: 1000 }, { start: 0, end: 500 })).toEqual({
      start: 0,
      end: 500,
    })
  })
  it('sorts projects by their earliest task, with name as a tie-breaker and updated row offsets', () => {
    const tasks = [
      task(':a:later', 300, 350),
      task(':z:first', 50, 150),
      task(':b:tie', 50, 100),
      task(':a:first', 100, 200),
    ]
    expect(groupTasks(tasks).map((group) => group.name)).toEqual([':a', ':b', ':z'])
    const groups = groupTasks(tasks, 'start-time')
    expect(groups.map((group) => group.name)).toEqual([':b', ':z', ':a'])
    expect(groups[0]!.top).toBe(0)
    expect(groups[1]!.top).toBe(groups[0]!.height)
    expect(groups[2]!.top).toBe(groups[0]!.height + groups[1]!.height)
    expect(groupTasks([], 'start-time')).toEqual([])
  })
  it('groups nested and root tasks and reuses nonoverlapping lanes', () => {
    expect(projectOf(task(':help'))).toBe(':')
    const groups = groupTasks([
      task(':a:b:x', 0, 10),
      task(':a:b:y', 5, 15),
      task(':a:b:z', 15, 20),
    ])
    expect(groups[0]!.name).toBe(':a:b')
    expect(groups[0]!.bars.map((b) => b.lane)).toEqual([0, 1, 0])
  })
})
