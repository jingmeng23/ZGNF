<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { BusinessDaily, BusinessMetrics } from '../api/statistics'
import { chartScale } from '../admin/overview'

const props = defineProps<{
  days: BusinessDaily[]
  metric: keyof BusinessMetrics
  label: string
  currency: boolean
}>()
const host = ref<HTMLElement>(),
  width = ref(650),
  active = ref(-1)
const height = 245,
  left = 55,
  right = 19,
  top = 22,
  bottom = 35
const values = computed(() => props.days.map((day) => Number(day.metrics[props.metric])))
const scale = computed(() => chartScale(values.value, !props.currency))
const innerWidth = computed(() => Math.max(1, width.value - left - right))
const x = (index: number) =>
  props.days.length <= 1
    ? left + innerWidth.value / 2
    : left + (index * innerWidth.value) / (props.days.length - 1)
const y = (value: number) =>
  top + ((scale.value.max - value) / (scale.value.max - scale.value.min)) * (height - top - bottom)
const line = computed(() =>
  values.value.map((value, index) => `${index ? 'L' : 'M'}${x(index)},${y(value)}`).join(' '),
)
const area = computed(() =>
  values.value.length
    ? `${line.value} L${x(values.value.length - 1)},${y(0)} L${x(0)},${y(0)} Z`
    : '',
)
const selectedIndex = computed(() =>
  Math.min(
    Math.max(active.value < 0 ? props.days.length - 1 : active.value, 0),
    props.days.length - 1,
  ),
)
const selected = computed(() => props.days[selectedIndex.value])
const dateLabels = computed(() => {
  const count = Math.min(props.days.length, width.value < 400 ? 3 : 6)
  return [
    ...new Set(
      Array.from({ length: count }, (_, index) =>
        count === 1 ? 0 : Math.round((index * (props.days.length - 1)) / (count - 1)),
      ),
    ),
  ]
})
function number(value: number) {
  return Math.abs(value) >= 10000
    ? `${Number((value / 10000).toFixed(1))}万`
    : Number(value.toFixed(2)).toLocaleString('zh-CN')
}
function selectByPointer(event: PointerEvent) {
  const svg = event.currentTarget as SVGElement
  const position =
    ((event.clientX - svg.getBoundingClientRect().left) / svg.getBoundingClientRect().width) *
    width.value
  active.value =
    props.days.length <= 1
      ? 0
      : Math.max(
          0,
          Math.min(
            props.days.length - 1,
            Math.round(((position - left) / innerWidth.value) * (props.days.length - 1)),
          ),
        )
}
function keyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  active.value =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? props.days.length - 1
        : Math.max(
            0,
            Math.min(
              props.days.length - 1,
              selectedIndex.value + (event.key === 'ArrowLeft' ? -1 : 1),
            ),
          )
}
let observer: ResizeObserver | undefined
onMounted(() => {
  if (!host.value) return
  width.value = Math.max(1, host.value.clientWidth)
  observer = new ResizeObserver((entries) => {
    width.value = Math.max(1, entries[0].contentRect.width)
  })
  observer.observe(host.value)
})
watch(
  () => [props.days, props.metric],
  () => {
    active.value = -1
  },
)
onUnmounted(() => observer?.disconnect())
</script>
<template>
  <div ref="host" class="business-chart">
    <div class="chart-reading" aria-live="polite">
      <span><i />{{ label }}</span
      ><span v-if="selected"
        ><time>{{ selected.date }}</time
        ><strong
          >{{ currency ? '¥' : ''
          }}{{
            Number(selected.metrics[metric]).toLocaleString('zh-CN', {
              minimumFractionDigits: currency ? 2 : 0,
              maximumFractionDigits: currency ? 2 : 0,
            })
          }}{{ currency ? '' : ' 单' }}</strong
        ></span
      >
    </div>
    <svg
      v-if="days.length"
      :viewBox="`0 0 ${width} ${height}`"
      role="img"
      tabindex="0"
      :aria-label="`${label}按日趋势，${days[0].date}至${days[days.length - 1].date}。使用左右方向键查看每日数据。`"
      @pointermove="selectByPointer"
      @pointerdown="selectByPointer"
      @keydown="keyboard"
    >
      <title>{{ label }}按日趋势</title>
      <text :x="left - 8" y="11" text-anchor="end" class="unit">{{ currency ? '元' : '单' }}</text>
      <g v-for="tick in scale.ticks" :key="tick">
        <line
          :x1="left"
          :x2="width - right"
          :y1="y(tick)"
          :y2="y(tick)"
          :class="{ baseline: tick === 0 }"
        />
        <text :x="left - 10" :y="y(tick) + 4" text-anchor="end">{{ number(tick) }}</text>
      </g>
      <path :d="area" class="area" />
      <path :d="line" class="line" />
      <g v-for="index in dateLabels" :key="index">
        <text
          :x="x(index)"
          :y="height - 10"
          :text-anchor="
            index === 0 && days.length > 1
              ? 'start'
              : index === days.length - 1 && days.length > 1
                ? 'end'
                : 'middle'
          "
        >
          {{ days[index].date.slice(5).replace('-', '/') }}
        </text>
      </g>
      <g v-if="selected">
        <line
          :x1="x(selectedIndex)"
          :x2="x(selectedIndex)"
          :y1="top"
          :y2="height - bottom"
          class="selection"
        />
        <circle
          :cx="x(selectedIndex)"
          :cy="y(Number(selected.metrics[metric]))"
          r="5"
          class="point"
        />
      </g>
      <circle v-if="days.length === 1" :cx="x(0)" :cy="y(values[0])" r="5" class="point" />
    </svg>
    <p class="chart-help">
      {{
        values.every((value) => value === 0)
          ? '所选日期暂无此项数据。'
          : days.length === 1
            ? '当前为单日数据，可选择近 7 天或更长范围查看变化。'
            : '悬停、轻触或使用左右方向键查看每日数据。'
      }}
    </p>
  </div>
</template>
<style scoped>
.business-chart {
  width: 100%;
  min-width: 0;
}
.chart-reading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  min-height: 43px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #64796b;
}
.chart-reading > span {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.chart-reading i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #39845b;
}
.chart-reading strong {
  font-size: 14px;
  color: #355b42;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
svg {
  display: block;
  width: 100%;
  overflow: visible;
  outline-offset: 3px;
}
svg text {
  font-family: inherit;
  font-size: 11px;
  fill: #6c8072;
}
svg line {
  stroke: #eaf0e9;
  stroke-dasharray: 3 4;
}
svg .baseline {
  stroke: #dce6dc;
  stroke-dasharray: none;
}
.line {
  fill: none;
  stroke: #3b885c;
  stroke-width: 2.5;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.area {
  fill: #4a926b12;
}
svg .selection {
  stroke: #a8c3b0;
  stroke-dasharray: 4 4;
}
.point {
  fill: #39845b;
  stroke: white;
  stroke-width: 3;
}
.chart-help {
  font-size: 12px;
  line-height: 1.7;
  color: #6c8072;
  margin: 6px 0 0;
}
@media (max-width: 420px) {
  .chart-reading {
    flex-wrap: wrap;
    gap: 5px;
  }
  .chart-reading strong {
    font-size: 14px;
  }
}
</style>
