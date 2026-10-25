<script setup lang="ts">
import { computed } from 'vue'
import type { Range } from '../../recording'
const props = defineProps<{
  points: { time: number; count: number }[]
  downloadActivity: Range[]
  full: Range
  x: (time: number) => number
}>()
const path = computed(() => {
  const peak = Math.max(
    1,
    props.points.reduce((max, p) => Math.max(max, p.count), 0),
  )
  let d = `M ${props.x(props.full.start)} 76`
  for (const point of props.points)
    d += ` H ${props.x(point.time)} V ${76 - (point.count / peak) * 48}`
  return d + ` H ${props.x(props.full.end)} V 76 Z`
})
</script>
<template>
  <g class="parallel-graph">
    <rect width="1000" height="100" fill="#f7fafb" />
    <g class="download-activity">
      <rect
        v-for="(interval, index) in downloadActivity"
        :key="index"
        :x="x(interval.start)"
        :width="x(interval.end) - x(interval.start)"
        y="4"
        height="88"
        fill="#d9ac52"
        fill-opacity=".22"
        ><title>Download activity</title></rect
      >
    </g>
    <path
      :d="path"
      fill="#d9eeea"
      fill-opacity=".65"
      stroke="#379585"
      stroke-width="1.5"
      vector-effect="non-scaling-stroke"
    />
  </g>
</template>
