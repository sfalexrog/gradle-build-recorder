<script setup lang="ts">
import type { taskDependencies } from '../../recording'
defineProps<{
  links: ReturnType<typeof taskDependencies>
  x: (time: number) => number
  markerId: string
}>()
</script>
<template>
  <g class="dependency-links" pointer-events="none">
    <defs
      ><marker
        :id="markerId"
        viewBox="0 0 10 10"
        refX="9"
        refY="5"
        markerWidth="6"
        markerHeight="6"
        orient="auto-start-reverse"
        ><path d="M 0 0 L 10 5 L 0 10 z" fill="#8b59bc" /></marker
    ></defs>
    <path
      v-for="(link, index) in links"
      :key="index"
      :data-dependency="link.source.task.taskPath"
      :d="`M ${x(link.source.task.event.endTime)} ${link.source.y} C ${x(link.source.task.event.endTime) + 35} ${link.source.y}, ${x(link.target.task.event.startTime) - 35} ${link.target.y}, ${x(link.target.task.event.startTime)} ${link.target.y}`"
      fill="none"
      stroke="#8b59bc"
      stroke-width="1.5"
      vector-effect="non-scaling-stroke"
      :marker-end="`url(#${markerId})`"
    />
  </g>
</template>
