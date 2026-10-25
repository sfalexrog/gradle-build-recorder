<script setup lang="ts">
import { duration, status } from '../../recording'
import type { Task, TaskGroup } from '../../recording'
defineProps<{
  groups: TaskGroup[]
  x: (time: number) => number
  span: number
  selectedProject: string | null
  hovered: string | null
  selectedTask: Task | null
  dependencies: Set<Task>
}>()
defineEmits<{ hover: [project: string | null]; selectTask: [task: Task] }>()
</script>
<template>
  <g
    v-for="group in groups"
    :key="group.name"
    :data-project="group.name"
    @mouseenter="$emit('hover', group.name)"
    @mouseleave="$emit('hover', null)"
  >
    <rect
      x="0"
      :y="100 + group.top"
      width="1000"
      :height="group.height"
      :fill="selectedProject === group.name || hovered === group.name ? '#eef4ff' : '#fff'"
    />
    <line
      x1="0"
      x2="1000"
      :y1="100 + group.top + group.height"
      :y2="100 + group.top + group.height"
      stroke="#edf0f3"
    />
    <rect
      v-for="(bar, index) in group.bars"
      :key="index"
      :data-task="bar.task.taskPath"
      :data-task-index="index"
      :x="x(bar.task.event.startTime)"
      :y="110 + group.top + bar.lane * 22"
      :width="Math.max(2, ((bar.task.event.endTime - bar.task.event.startTime) / span) * 1000)"
      height="15"
      rx="2"
      :class="[
        status(bar.task),
        {
          'selected-task': selectedTask === bar.task,
          'dependency-task': dependencies.has(bar.task),
        },
      ]"
      tabindex="0"
      role="button"
      :aria-label="`Select task ${bar.task.taskPath}`"
      :aria-pressed="selectedTask === bar.task"
      @keydown.enter.prevent="$emit('selectTask', bar.task)"
      @keydown.space.prevent="$emit('selectTask', bar.task)"
    >
      <title>
        {{ bar.task.taskPath }} ·
        {{ duration(bar.task.event.endTime - bar.task.event.startTime) }} · {{ status(bar.task) }}
      </title>
    </rect>
  </g>
</template>
