<script setup lang="ts">
import { computed, ref, useId, watch, onMounted, onUnmounted } from 'vue'
import {
  assignLanes,
  clampView,
  downloadActivity,
  duration,
  parallelCountAt,
  taskDependencies,
  ticks,
} from '../../recording'
import type { Download, Range, Task, TaskGroup } from '../../recording'
import TimeAxis from './TimeAxis.vue'
import TimeCursor from './TimeCursor.vue'
import ParallelGraph from './ParallelGraph.vue'
import TaskRows from './TaskRows.vue'
import DownloadRows from './DownloadRows.vue'
import DependencyLinks from './DependencyLinks.vue'

const props = defineProps<{
  groups: TaskGroup[]
  downloads: Download[]
  full: Range
  view: Range
  selection: Range | null
  selectedProject: string | null
  selectedTask: Task | null
  selectedDownload?: Download | null
  pinnedTime?: number | null
  mode: 'select' | 'pan'
  concurrency: { time: number; count: number }[]
  timeLabel: (time: number) => string
}>()
const emit = defineEmits<{
  'update:view': [view: Range]
  'update:pinnedTime': [time: number | null]
  'update:cursor': [time: number | null]
  'update:selection': [selection: Range | null]
  selectProject: [project: string | null]
  selectTask: [task: Task]
  selectDownload: [download: Download]
  showDownloads: []
  zoom: [factor: number, anchor: number]
}>()
const plot = ref<SVGSVGElement>()
const graphPlot = ref<SVGSVGElement>()
const scroll = ref<HTMLDivElement>()
const hovered = ref<string | null>(null)
const id = useId()
const clipId = `timeline-clip-${id}`
const markerId = `dependency-arrow-${id}`
const span = computed(() => props.view.end - props.view.start)
const x = (time: number) => ((time - props.view.start) / span.value) * 1000
const taskHeight = computed(() => props.groups.reduce((sum, group) => sum + group.height, 0))
const downloadBars = computed(() => assignLanes(props.downloads, (item) => item))
const activity = computed(() => downloadActivity(props.downloads, props.full))
const downloadTime = computed(() =>
  activity.value.reduce((total, interval) => total + interval.end - interval.start, 0),
)
const downloadHeight = computed(() =>
  downloadBars.value.length
    ? Math.max(
        48,
        downloadBars.value.reduce((max, bar) => Math.max(max, bar.lane + 1), 0) * 22 + 18,
      )
    : 0,
)
const chartHeight = computed(() => 100 + Math.max(160, taskHeight.value + downloadHeight.value))
const tickValues = computed(() =>
  ticks({ start: props.view.start - props.full.start, end: props.view.end - props.full.start }).map(
    (t) => t + props.full.start,
  ),
)
const links = computed(() => taskDependencies(props.groups, props.selectedTask))
const dependencies = computed(() => new Set(links.value.map((link) => link.source.task)))
const peak = computed(() => props.concurrency.reduce((max, p) => Math.max(max, p.count), 0))
// Store a screen ratio for hover, and an absolute timestamp for the pinned cursor.
// This keeps hover under the pointer as the viewport changes and pins attached to build time.
const hoverRatio = ref<number | null>(null)
const cursor = computed(
  () =>
    props.pinnedTime ??
    (hoverRatio.value === null ? null : props.view.start + hoverRatio.value * span.value),
)
const cursorCount = computed(() =>
  cursor.value === null ? 0 : parallelCountAt(props.concurrency, cursor.value),
)
watch(cursor, (time) => emit('update:cursor', time), { immediate: true })
function trackPointer(event: PointerEvent) {
  const rect = plot.value!.getBoundingClientRect()
  const inside = [rect, graphPlot.value?.getBoundingClientRect()].some(
    (bounds) =>
      bounds &&
      event.clientX >= bounds.left &&
      event.clientX <= bounds.right &&
      event.clientY >= bounds.top &&
      event.clientY <= bounds.bottom,
  )
  hoverRatio.value = inside
    ? Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width))
    : null
}
function escape(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('update:pinnedTime', null)
    emit('update:selection', null)
  }
}
onMounted(() => window.addEventListener('keydown', escape))
onUnmounted(() => window.removeEventListener('keydown', escape))
watch(
  () => props.groups,
  () => {
    hoverRatio.value = null
  },
)
function timeAt(event: PointerEvent | WheelEvent) {
  const rect = plot.value!.getBoundingClientRect()
  return (
    props.view.start +
    Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)) * span.value
  )
}
let drag: {
  start: number
  clientX: number
  clientY: number
  initial: Range
  pan: boolean
  moved: boolean
  target: Element | null
  capture: SVGSVGElement
} | null = null
function pointerDown(event: PointerEvent) {
  if (event.button !== 0) return
  scroll.value?.focus({ preventScroll: true })
  drag = {
    start: timeAt(event),
    clientX: event.clientX,
    clientY: event.clientY,
    initial: { ...props.view },
    pan: props.mode === 'pan' || event.shiftKey,
    moved: false,
    target: event.target instanceof Element ? event.target : null,
    capture: event.currentTarget as SVGSVGElement,
  }
  drag.capture.setPointerCapture(event.pointerId)
}
function pointerMove(event: PointerEvent) {
  trackPointer(event)
  if (!drag) return
  if (
    Math.abs(event.clientX - drag.clientX) < 3 &&
    Math.abs(event.clientY - drag.clientY) < 3 &&
    !drag.moved
  )
    return
  drag.moved = true
  if (drag.pan) {
    const delta =
      ((event.clientX - drag.clientX) / plot.value!.getBoundingClientRect().width) *
      (drag.initial.end - drag.initial.start)
    emit(
      'update:view',
      clampView({ start: drag.initial.start - delta, end: drag.initial.end - delta }, props.full),
    )
  } else {
    const end = timeAt(event)
    if (end !== drag.start)
      emit('update:selection', { start: Math.min(drag.start, end), end: Math.max(drag.start, end) })
  }
}
function pointerUp(event: PointerEvent) {
  if (drag && !drag.moved) {
    emit('update:pinnedTime', timeAt(event))
  }
  if (drag && !drag.moved && !drag.pan) {
    const bar = drag.target?.closest('[data-task]')
    const project = drag.target?.closest('[data-project]')?.getAttribute('data-project')
    // Use the original pointer target: pointer capture retargets pointerup to the SVG root.
    const group = props.groups.find((group) => group.name === project)
    const task = bar ? group?.bars[Number(bar.getAttribute('data-task-index'))]?.task : undefined
    const downloadBar = drag.target?.closest('[data-download]')
    const download = downloadBar
      ? downloadBars.value[Number(downloadBar.getAttribute('data-download-index'))]?.item
      : undefined
    if (download) emit('selectDownload', download)
    else if (task) emit('selectTask', task)
    else if (project) emit('selectProject', project)
  }
  if (drag?.capture.hasPointerCapture(event.pointerId))
    drag.capture.releasePointerCapture(event.pointerId)
  drag = null
}
function pan(delta: number) {
  emit(
    'update:view',
    clampView({ start: props.view.start + delta, end: props.view.end + delta }, props.full),
  )
}
/** Vertical wheel movement scrolls rows; horizontal movement pans time. Normalize wheel units. */
function wheel(event: WheelEvent) {
  const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? scroll.value!.clientHeight : 1
  if (event.ctrlKey || event.metaKey) {
    event.preventDefault()
    emit('zoom', Math.exp(Math.max(-2, Math.min(2, event.deltaY * scale * 0.005))), timeAt(event))
  } else if (event.shiftKey || Math.abs(event.deltaX) > Math.abs(event.deltaY)) {
    event.preventDefault()
    pan((((event.deltaX || event.deltaY) * scale) / 1000) * span.value)
  } else {
    event.preventDefault()
    scroll.value!.scrollTop += event.deltaY * scale
  }
}
function keydown(event: KeyboardEvent) {
  // Arrow/page navigation also works when a task bar or project label has focus.
  const viewport = scroll.value!
  const scrollBy =
    event.key === 'ArrowDown'
      ? 48
      : event.key === 'ArrowUp'
        ? -48
        : event.key === 'PageDown'
          ? viewport.clientHeight * 0.8
          : event.key === 'PageUp'
            ? -viewport.clientHeight * 0.8
            : 0
  if (scrollBy) {
    event.preventDefault()
    viewport.scrollTop += scrollBy
  } else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    viewport.scrollTop = event.key === 'Home' ? 0 : viewport.scrollHeight
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault()
    pan(span.value * (event.key === 'ArrowLeft' ? -0.1 : 0.1))
  }
}
</script>
<template>
  <div
    ref="scroll"
    class="timeline-scroll"
    tabindex="0"
    role="region"
    aria-label="Build timeline. Up and down scroll rows; left and right pan time."
    @wheel="wheel"
    @keydown="keydown"
  >
    <div class="timeline-grid">
      <div class="project-labels" :style="{ height: `${chartHeight}px` }">
        <div class="metric-label">
          PARALLEL TASKS<small>peak {{ peak }}</small
          ><small class="download-activity-label">Downloads: {{ duration(downloadTime) }}</small>
        </div>
        <button
          v-for="group in groups"
          :key="group.name"
          :style="{ top: `${100 + group.top}px`, height: `${group.height}px` }"
          :class="{ highlighted: hovered === group.name, selected: selectedProject === group.name }"
          @mouseenter="hovered = group.name"
          @mouseleave="hovered = null"
          @click="emit('selectProject', selectedProject === group.name ? null : group.name)"
        >
          <strong>{{ group.name }}</strong
          ><small>{{ group.tasks.length }} tasks</small>
        </button>
        <button
          v-if="downloads.length"
          class="download-label"
          @click="emit('showDownloads')"
          :style="{ top: `${100 + taskHeight}px`, height: `${downloadHeight}px` }"
        >
          <strong>Downloads</strong><small>{{ downloads.length }} files</small>
        </button>
      </div>
      <div class="svg-column">
        <svg
          ref="graphPlot"
          class="parallel-svg"
          :class="mode"
          viewBox="0 0 1000 100"
          preserveAspectRatio="none"
          role="img"
          aria-label="Parallel tasks graph"
          @pointerdown="pointerDown"
          @pointerenter="trackPointer"
          @pointerleave="hoverRatio = null"
          @pointermove="pointerMove"
          @pointerup="pointerUp"
          @pointercancel="drag = null"
        >
          <ParallelGraph :points="concurrency" :full="full" :x="x" :download-activity="activity" />
          <g pointer-events="none">
            <line
              v-for="tick in tickValues"
              :key="tick"
              :x1="x(tick)"
              :x2="x(tick)"
              y1="0"
              y2="100"
              stroke="#e6ebef"
            />
          </g>
          <rect
            v-if="selection"
            :x="x(selection.start)"
            :width="((selection.end - selection.start) / span) * 1000"
            y="0"
            height="100"
            fill="#436fe51f"
            stroke="#5376d1"
            pointer-events="none"
          />
          <TimeCursor
            v-if="cursor !== null"
            :x="x(cursor)"
            :height="100"
            :count="cursorCount"
            :label="timeLabel(cursor)"
            :pinned="pinnedTime != null"
          />
        </svg>
        <svg
          ref="plot"
          class="timeline-svg"
          :class="mode"
          :viewBox="`0 100 1000 ${chartHeight - 100}`"
          preserveAspectRatio="none"
          :style="{ height: `${chartHeight - 100}px` }"
          role="img"
          aria-label="Task and download timeline. Drag to select a time range or use Pan mode to navigate."
          @pointerdown="pointerDown"
          @pointerenter="trackPointer"
          @pointerleave="hoverRatio = null"
          @pointermove="pointerMove"
          @pointerup="pointerUp"
          @pointercancel="drag = null"
        >
          <defs>
            <clipPath :id="clipId"><rect width="1000" :height="chartHeight" /></clipPath>
          </defs>
          <g :clip-path="`url(#${clipId})`">
            <TaskRows
              :groups="groups"
              :x="x"
              :span="span"
              :selected-project="selectedProject"
              :hovered="hovered"
              :selected-task="selectedTask"
              :dependencies="dependencies"
              @hover="hovered = $event"
              @select-task="emit('selectTask', $event)"
            />
            <DownloadRows
              :bars="downloadBars"
              :selected="selectedDownload"
              @select="emit('selectDownload', $event)"
              :top="100 + taskHeight"
              :height="downloadHeight"
              :x="x"
              :span="span"
            />
            <g pointer-events="none">
              <line
                v-for="tick in tickValues"
                :key="tick"
                :x1="x(tick)"
                :x2="x(tick)"
                y1="0"
                :y2="chartHeight"
                stroke="#e6ebef"
              />
            </g>
            <DependencyLinks :links="links" :x="x" :marker-id="markerId" />
            <rect
              v-if="selection"
              :x="x(selection.start)"
              :width="((selection.end - selection.start) / span) * 1000"
              y="0"
              :height="chartHeight"
              fill="#436fe51f"
              stroke="#5376d1"
              pointer-events="none"
            />
            <TimeCursor
              v-if="cursor !== null"
              :x="x(cursor)"
              :height="chartHeight"
              :count="cursorCount"
              :label="timeLabel(cursor)"
              :pinned="pinnedTime != null"
            />
          </g>
        </svg>
        <TimeAxis :ticks="tickValues" :x="x" :label="timeLabel" />
      </div>
    </div>
  </div>
</template>
