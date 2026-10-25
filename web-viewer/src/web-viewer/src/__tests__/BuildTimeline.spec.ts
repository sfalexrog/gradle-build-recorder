import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BuildTimeline from '../components/timeline/BuildTimeline.vue'
import {
  groupTasks,
  taskDependencies,
  downloadActivity,
  intersectsTime,
  parallelCountAt,
  parallelism,
} from '../recording'
import { task } from './fixtures'
import type { Task } from '../recording'

function dispatch(element: Element, type: string, properties: Record<string, number>) {
  const event = new Event(type, { bubbles: true })
  Object.assign(event, properties)
  element.dispatchEvent(event)
}
function timeline(tasks: Task[] = [task()]) {
  return mount(BuildTimeline, {
    props: {
      groups: groupTasks(tasks),
      downloads: [],
      full: { start: 0, end: 500 },
      view: { start: 100, end: 400 },
      selection: null,
      selectedProject: null,
      selectedTask: null,
      mode: 'select',
      concurrency: [],
      timeLabel: String,
    },
  })
}
describe('timeline navigation', () => {
  it('scrolls vertically without panning and normalizes line wheel units', async () => {
    const wrapper = timeline()
    const scroll = wrapper.get('.timeline-scroll')
    await scroll.trigger('wheel', { deltaY: 5, deltaMode: 1 })
    expect(scroll.element.scrollTop).toBe(80)
    expect(wrapper.emitted('update:view')).toBeUndefined()
    await scroll.trigger('keydown', { key: 'ArrowUp' })
    expect(scroll.element.scrollTop).toBe(32)
    await scroll.trigger('keydown', { key: 'ArrowDown' })
    expect(scroll.element.scrollTop).toBe(80)
  })
  it('pans horizontally and zooms around the pointer without scrolling rows', async () => {
    const wrapper = timeline()
    const scroll = wrapper.get('.timeline-scroll')
    vi.spyOn(wrapper.get('.timeline-svg').element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      width: 1000,
    } as DOMRect)
    await scroll.trigger('wheel', { deltaX: 100, deltaY: 0 })
    expect(wrapper.emitted('update:view')?.[0]).toEqual([{ start: 130, end: 430 }])
    scroll.element.dispatchEvent(
      new WheelEvent('wheel', { bubbles: true, deltaY: -10, ctrlKey: true, clientX: 500 }),
    )
    expect(wrapper.emitted('zoom')?.[0]?.[1]).toBe(250)
    expect(scroll.element.scrollTop).toBe(0)
    scroll.element.dispatchEvent(
      new WheelEvent('wheel', { bubbles: true, deltaY: 100, shiftKey: true }),
    )
    expect(wrapper.emitted('update:view')).toHaveLength(2)
  })
})
describe('downloads and dependencies', () => {
  it('packs overlapping downloads and shows successful and failed outcomes', async () => {
    const wrapper = timeline([])
    await wrapper.setProps({
      downloads: [
        {
          startTime: 0,
          endTime: 200,
          uri: 'https://example.org/a.jar',
          bytesDownloaded: 1024,
          result: { type: 'success' },
        },
        {
          startTime: 100,
          endTime: 300,
          uri: 'https://example.org/b.jar',
          bytesDownloaded: 0,
          result: { type: 'not_found' },
        },
      ],
    })
    const bars = wrapper.findAll('[data-download]')
    expect(bars).toHaveLength(2)
    expect(bars[0]!.attributes('y')).not.toBe(bars[1]!.attributes('y'))
    expect(bars[0]!.get('title').text()).toContain('1,024 bytes')
    expect(bars[1]!.classes()).toContain('download-failed')
    expect(wrapper.get('.download-label').text()).toContain('2 files')
    expect(wrapper.findAll('.download-activity rect')).toHaveLength(1)
    expect(wrapper.get('.download-activity rect').attributes('width')).toBe('1000')
  })
  it('draws direct dependency links across project rows and clears them', async () => {
    const source = task(':lib:compile', 0, 100)
    const target = {
      ...task(':app:compile', 150, 300),
      dependencies: [source.taskPath, ':missing'],
    }
    const wrapper = timeline([source, target])
    await wrapper.setProps({ selectedTask: target })
    expect(wrapper.findAll('[data-dependency]')).toHaveLength(1)
    expect(wrapper.get('[data-dependency]').attributes('data-dependency')).toBe(source.taskPath)
    expect(wrapper.findAll('.dependency-task')).toHaveLength(1)
    expect(wrapper.findAll('.selected-task')).toHaveLength(1)
    await wrapper.get(`[data-task="${target.taskPath}"]`).trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('selectTask')?.[0]).toEqual([target])
    await wrapper.setProps({ selectedTask: null })
    expect(wrapper.findAll('[data-dependency]')).toHaveLength(0)
  })
  it('selects task bars on a click without turning a drag into a task selection', async () => {
    const selected = task()
    const wrapper = timeline([selected])
    const svg = wrapper.get('.timeline-svg')
    vi.spyOn(svg.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      width: 1000,
    } as DOMRect)
    Object.assign(svg.element, {
      setPointerCapture: vi.fn<() => void>(),
      hasPointerCapture: () => false,
    })
    const bar = wrapper.get('[data-task]')
    dispatch(bar.element, 'pointerdown', { button: 0, clientX: 100, clientY: 100, pointerId: 1 })
    dispatch(svg.element, 'pointerup', { clientX: 100, clientY: 100, pointerId: 1 })
    expect(wrapper.emitted('selectTask')?.[0]).toEqual([selected])
    dispatch(bar.element, 'pointerdown', { button: 0, clientX: 100, clientY: 100, pointerId: 1 })
    dispatch(svg.element, 'pointermove', { clientX: 200, clientY: 100, pointerId: 1 })
    dispatch(svg.element, 'pointerup', { clientX: 200, clientY: 100, pointerId: 1 })
    expect(wrapper.emitted('selectTask')).toHaveLength(1)
    expect(wrapper.emitted('update:selection')).toHaveLength(1)
  })
  it('selects downloads by pointer and keyboard and opens the inspector from the row label', async () => {
    const wrapper = timeline([])
    const download = {
      startTime: 100,
      endTime: 200,
      uri: 'a.jar',
      bytesDownloaded: 100,
      result: { type: 'success' as const },
    }
    await wrapper.setProps({ downloads: [download] })
    const svg = wrapper.get('.timeline-svg')
    vi.spyOn(svg.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      width: 1000,
    } as DOMRect)
    Object.assign(svg.element, {
      setPointerCapture: vi.fn<() => void>(),
      hasPointerCapture: () => false,
    })
    dispatch(wrapper.get('[data-download]').element, 'pointerdown', {
      button: 0,
      clientX: 100,
      clientY: 100,
      pointerId: 1,
    })
    dispatch(svg.element, 'pointerup', { clientX: 100, clientY: 100, pointerId: 1 })
    expect(wrapper.emitted('selectDownload')?.[0]).toEqual([download])
    await wrapper.get('[data-download]').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('selectDownload')).toHaveLength(2)
    await wrapper.get('.download-label').trigger('click')
    expect(wrapper.emitted('showDownloads')).toHaveLength(1)
  })
  it('merges overlapping and adjacent download lifetimes and clips to the build', () => {
    const download = (startTime: number, endTime: number) => ({
      startTime,
      endTime,
      uri: 'a',
      bytesDownloaded: 0,
      result: { type: 'success' as const },
    })
    expect(
      downloadActivity(
        [
          download(-10, 20),
          download(10, 30),
          download(30, 40),
          download(60, 80),
          download(90, 90),
          download(101, 120),
        ],
        { start: 0, end: 100 },
      ),
    ).toEqual([
      { start: 0, end: 40 },
      { start: 60, end: 80 },
    ])
    expect(downloadActivity([], { start: 0, end: 100 })).toEqual([])
  })
  it('resolves repeated paths to the latest preceding task execution', () => {
    const earlier = task(':lib:compile', 0, 50)
    const later = task(':lib:compile', 50, 100)
    const target = { ...task(':app:compile', 150, 300), dependencies: [earlier.taskPath] }
    expect(taskDependencies(groupTasks([earlier, later, target]), target)[0]?.source.task).toBe(
      later,
    )
  })
})

describe('time cursor', () => {
  it('shares cursor and range interaction between the pinned graph and task rows', async () => {
    const wrapper = timeline()
    const graph = wrapper.get('.parallel-svg')
    const plot = wrapper.get('.timeline-svg')
    vi.spyOn(plot.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      right: 1000,
      top: 100,
      bottom: 1000,
      width: 1000,
    } as DOMRect)
    vi.spyOn(graph.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      right: 1000,
      top: 0,
      bottom: 100,
      width: 1000,
    } as DOMRect)
    Object.assign(graph.element, {
      setPointerCapture: vi.fn<() => void>(),
      hasPointerCapture: () => false,
    })
    dispatch(graph.element, 'pointerenter', { clientX: 500, clientY: 20 })
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.parallel-svg .time-cursor line').attributes('x1')).toBe('500')
    expect(wrapper.get('.timeline-svg .time-cursor line').attributes('x1')).toBe('500')
    dispatch(graph.element, 'pointerdown', { button: 0, clientX: 500, clientY: 20, pointerId: 1 })
    dispatch(graph.element, 'pointerup', { clientX: 500, clientY: 20, pointerId: 1 })
    expect(wrapper.emitted('update:pinnedTime')?.slice(-1)[0]).toEqual([250])
    dispatch(graph.element, 'pointerdown', { button: 0, clientX: 100, clientY: 20, pointerId: 1 })
    dispatch(graph.element, 'pointermove', { clientX: 500, clientY: 20, pointerId: 1 })
    dispatch(graph.element, 'pointerup', { clientX: 500, clientY: 20, pointerId: 1 })
    expect(wrapper.emitted('update:selection')?.slice(-1)[0]).toEqual([{ start: 130, end: 250 }])
    wrapper.unmount()
  })
  it('clears a selected range and pinned cursor together on Escape', async () => {
    const wrapper = timeline()
    await wrapper.setProps({ selection: { start: 100, end: 200 }, pinnedTime: 150 })
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('update:selection')?.slice(-1)[0]).toEqual([null])
    expect(wrapper.emitted('update:pinnedTime')?.slice(-1)[0]).toEqual([null])
    wrapper.unmount()
  })
  it('follows hover, pins time through viewport changes, and releases on Escape', async () => {
    const wrapper = timeline()
    const svg = wrapper.get('.timeline-svg')
    vi.spyOn(svg.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      right: 1000,
      top: 0,
      bottom: 1000,
      width: 1000,
    } as DOMRect)
    Object.assign(svg.element, {
      setPointerCapture: vi.fn<() => void>(),
      hasPointerCapture: () => false,
    })
    dispatch(svg.element, 'pointerenter', { clientX: 500, clientY: 20 })
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.time-cursor line').attributes('x1')).toBe('500')
    expect(wrapper.emitted('update:cursor')?.slice(-1)[0]).toEqual([250])
    dispatch(svg.element, 'pointerdown', { button: 0, clientX: 200, clientY: 20, pointerId: 1 })
    dispatch(svg.element, 'pointerup', { clientX: 200, clientY: 20, pointerId: 1 })
    expect(wrapper.emitted('update:pinnedTime')?.slice(-1)[0]).toEqual([160])
    await wrapper.setProps({ pinnedTime: 160 })
    dispatch(svg.element, 'pointermove', { clientX: 900, clientY: 20 })
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update:cursor')?.slice(-1)[0]).toEqual([160])
    await wrapper.setProps({ view: { start: 0, end: 200 } })
    expect(wrapper.get('.time-cursor line').attributes('x1')).toBe('800')
    await svg.trigger('pointerleave')
    expect(wrapper.find('.time-cursor').exists()).toBe(true)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('update:pinnedTime')?.slice(-1)[0]).toEqual([null])
    await wrapper.setProps({ pinnedTime: null })
    expect(wrapper.find('.time-cursor').exists()).toBe(false)
    dispatch(svg.element, 'pointerenter', { clientX: 100, clientY: 20 })
    await wrapper.setProps({ pinnedTime: 160 })
    await wrapper.setProps({ pinnedTime: null })
    expect(wrapper.get('.time-cursor line').attributes('x1')).toBe('100')
    await svg.trigger('pointerleave')
    expect(wrapper.find('.time-cursor').exists()).toBe(false)
    wrapper.unmount()
  })
  it('counts actual execution with half-open boundaries and excludes cached/skipped work', () => {
    const a = task(':a', 100, 200),
      b = task(':b', 150, 250),
      cached = task(':cached', 0, 500)
    if (cached.event.type === 'success') cached.event.isFromCache = true
    const points = parallelism(
      [
        a,
        b,
        cached,
        { ...task(':skip'), event: { type: 'skipped', startTime: 0, endTime: 500, reason: '' } },
      ],
      { start: 0, end: 500 },
    )
    expect([99, 100, 150, 200, 250].map((time) => parallelCountAt(points, time))).toEqual([
      0, 1, 2, 1, 0,
    ])
    expect(intersectsTime(a.event, 200)).toBe(false)
    expect(intersectsTime(a.event, 100)).toBe(true)
    expect(intersectsTime({ startTime: 100, endTime: 100 }, 100)).toBe(true)
    expect(intersectsTime({ startTime: 100, endTime: 100 }, 100.5)).toBe(false)
  })
})
