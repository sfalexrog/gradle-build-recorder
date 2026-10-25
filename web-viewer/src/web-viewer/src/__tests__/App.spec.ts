import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from '../App.vue'
import { fixture } from './fixtures'

async function loaded(record = fixture) {
  const wrapper = mount(App)
  const input = wrapper.get('input[type=file]')
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [{ name: 'build.json', text: async () => JSON.stringify(record) }],
  })
  await input.trigger('change')
  await flushPromises()
  return wrapper
}
describe('viewer', () => {
  it('starts with an accessible local-file picker', () => {
    const wrapper = mount(App)
    expect(wrapper.text()).toContain('Open a build recording')
    expect(wrapper.find('input[type=file]').exists()).toBe(true)
  })
  it('loads a recording and filters project tasks without hiding unrelated logs', async () => {
    const wrapper = await loaded()
    expect(wrapper.findAll('.task-list article')).toHaveLength(2)
    await wrapper.get('.project-labels button').trigger('click')
    expect(wrapper.findAll('.task-list article')).toHaveLength(1)
    expect(wrapper.get('.task-list').text()).toContain(':app:compile')
    expect(wrapper.get('.log-lines').text()).toContain('warning')
    await wrapper.get('input[type=search]').setValue('hello')
    expect(wrapper.get('.log-lines').text()).toContain('hello')
    expect(wrapper.get('.log-lines').text()).not.toContain('warning')
  })
  it('selects a time range and filters tasks and logs together', async () => {
    const wrapper = await loaded()
    const svg = wrapper.get('.timeline-svg')
    vi.spyOn(svg.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      width: 1000,
    } as DOMRect)
    Object.assign(svg.element, {
      setPointerCapture: vi.fn<() => void>(),
      hasPointerCapture: () => false,
    })
    function pointer(type: string, clientX: number) {
      const event = new Event(type, { bubbles: true })
      Object.assign(event, { clientX, clientY: 0, button: 0, pointerId: 1 })
      svg.element.dispatchEvent(event)
    }
    pointer('pointerdown', 100)
    pointer('pointermove', 250)
    pointer('pointerup', 250)
    await svg.trigger('pointerleave')
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.task-count').text()).toContain('1 tasks in selected range')
    expect(wrapper.get('.log-lines').text()).toContain('hello')
    expect(wrapper.get('.log-lines').text()).not.toContain('warning')
    await wrapper.get('.range-bar button').trigger('click')
    expect(wrapper.findAll('.task-list article')).toHaveLength(2)
    expect(wrapper.get('.log-lines').text()).toContain('warning')
  })
  it('supports relative and local time displays and zoom controls', async () => {
    const wrapper = await loaded()
    const before = wrapper.get('.time-axis').text()
    await wrapper.get('button[aria-label="Zoom in"]').trigger('click')
    expect(wrapper.get('.time-axis').text()).not.toBe(before)
    await wrapper.get('select[aria-label="Time display"]').setValue(true)
    expect(wrapper.get('.time-axis').text()).toContain(':')
  })
  it('connects inspector task selection to dependency highlighting and clearing', async () => {
    const record = structuredClone(fixture)
    record.taskExecutionEvents[1]!.dependencies = [record.taskExecutionEvents[0]!.taskPath]
    const wrapper = await loaded(record)
    await wrapper.findAll('.task-select')[1]!.trigger('click')
    expect(wrapper.get('.dependency-summary').text()).toContain('1 direct dependencies')
    expect(wrapper.findAll('[data-dependency]')).toHaveLength(1)
    expect(wrapper.findAll('.selected-task')).toHaveLength(1)
    await wrapper.get('.dependency-summary button').trigger('click')
    expect(wrapper.findAll('[data-dependency]')).toHaveLength(0)
  })
  it('selects dependencies by name and labels absent dependencies', async () => {
    const record = structuredClone(fixture)
    record.taskExecutionEvents[1]!.dependencies = [
      record.taskExecutionEvents[0]!.taskPath,
      ':missing',
    ]
    const wrapper = await loaded(record)
    await wrapper.findAll('.task-select')[1]!.trigger('click')
    expect(wrapper.get('.dependency-names').text()).toContain(':missing (not recorded)')
    await wrapper.get('.dependency-select').trigger('click')
    expect(wrapper.get('.dependency-summary strong').text()).toBe(':app:compile')
    expect(wrapper.get('.panel-heading h2').text()).toBe(':app')
    expect(wrapper.get('.selected-task').attributes('data-task')).toBe(':app:compile')
  })
  it('inspects downloads and applies the selected time range', async () => {
    const record = structuredClone(fixture)
    record.fileDownloadEvents = [
      {
        startTime: 50,
        endTime: 100,
        uri: 'https://example.org/a.jar',
        bytesDownloaded: 100,
        result: { type: 'success' },
      },
      {
        startTime: 250,
        endTime: 300,
        uri: 'https://example.org/b.jar',
        bytesDownloaded: 0,
        result: { type: 'failure', messages: ['Connection lost'] },
      },
    ]
    const wrapper = await loaded(record)
    await wrapper.get('.inspector-tabs button:last-child').trigger('click')
    expect(wrapper.findAll('.download-list article')).toHaveLength(2)
    await wrapper.findAll('.download-select')[1]!.trigger('click')
    expect(wrapper.get('.download-summary').text()).toContain('Connection lost')
    expect(wrapper.get('.selected-download').attributes('data-download')).toBe(
      'https://example.org/b.jar',
    )
    const svg = wrapper.get('.timeline-svg')
    vi.spyOn(svg.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      width: 1000,
    } as DOMRect)
    Object.assign(svg.element, {
      setPointerCapture: vi.fn<() => void>(),
      hasPointerCapture: () => false,
    })
    for (const [type, clientX] of [
      ['pointerdown', 50],
      ['pointermove', 240],
      ['pointerup', 240],
    ] as const) {
      const event = new Event(type, { bubbles: true })
      Object.assign(event, { clientX, clientY: 0, button: 0, pointerId: 1 })
      svg.element.dispatchEvent(event)
    }
    await wrapper.vm.$nextTick()
    await svg.trigger('pointerleave')
    expect(wrapper.findAll('.download-list article')).toHaveLength(1)
    expect(wrapper.get('.download-summary').text()).toContain('outside the current range')
  })
  it('shows all intersected work while pinned and restores filters after clearing', async () => {
    const record = structuredClone(fixture)
    record.fileDownloadEvents = [
      {
        startTime: 160,
        endTime: 250,
        uri: 'a.jar',
        bytesDownloaded: 100,
        result: { type: 'success' },
      },
    ]
    const wrapper = await loaded(record)
    await wrapper.get('.project-labels button').trigger('click')
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
    const pointer = (type: string) => {
      const event = new Event(type, { bubbles: true })
      Object.assign(event, { clientX: 350, clientY: 20, button: 0, pointerId: 1 })
      svg.element.dispatchEvent(event)
    }
    pointer('pointerenter')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.task-count').text()).toContain('2 tasks at time cursor')
    expect(wrapper.get('.cursor-status').text()).toContain('2 running tasks')
    expect(wrapper.get('.cursor-status').text()).toContain('1 download in progress')
    pointer('pointerdown')
    pointer('pointerup')
    await wrapper.vm.$nextTick()
    await svg.trigger('pointerleave')
    expect(wrapper.find('.time-cursor').exists()).toBe(true)
    await wrapper.get('.inspector-tabs button:last-child').trigger('click')
    expect(wrapper.findAll('.download-list article')).toHaveLength(1)
    expect(wrapper.get('.download-inspector .task-count').text()).toContain('at time cursor')
    await wrapper.get('.cursor-status button').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.time-cursor').exists()).toBe(false)
    await wrapper.get('.inspector-tabs button:first-child').trigger('click')
    expect(wrapper.findAll('.task-list article')).toHaveLength(1)
    wrapper.unmount()
  })
  it('keeps a loaded recording when the next file is invalid', async () => {
    const wrapper = await loaded()
    const input = wrapper.get('input[type=file]')
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [{ name: 'bad.json', text: async () => '{}' }],
    })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toContain('Invalid recording')
    expect(wrapper.findAll('.task-list article')).toHaveLength(2)
  })
})
