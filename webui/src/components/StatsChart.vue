<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { init, use, type EChartsType } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { Stats } from '../types/api'
use([BarChart, GridComponent, TooltipComponent, SVGRenderer])
const props = defineProps<{ stats: Stats }>()
const container = ref<HTMLDivElement | null>(null)
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined
const values = computed(() =>
  [props.stats.userCount, props.stats.itemCount, props.stats.orderCount].map(Number)
)
const safe = computed(() =>
  values.value.every((value) => Number.isSafeInteger(value) && value >= 0)
)
function render() {
  if (!safe.value) return
  chart?.setOption({
    animation: false,
    grid: { left: 50, right: 30, top: 15, bottom: 30 },
    tooltip: { trigger: 'axis', renderMode: 'richText' },
    xAxis: {
      type: 'category',
      data: ['用户', '团购', '订单'],
      axisLine: { lineStyle: { color: '#dfe6da' } },
      axisTick: { show: false },
      axisLabel: { color: '#7d8d76' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf0e8' } },
      axisLabel: { color: '#9aa58e' },
    },
    series: [
      {
        type: 'bar',
        data: values.value,
        barWidth: 38,
        itemStyle: { color: '#7f9e6e', borderRadius: [5, 5, 0, 0] },
      },
    ],
  })
}
onMounted(() => {
  if (container.value) {
    chart = init(container.value, undefined, { renderer: 'svg' })
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(container.value)
    render()
  }
})
watch(() => props.stats, render)
onUnmounted(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>
<template>
  <div
    v-show="safe"
    ref="container"
    style="height: 260px"
    role="img"
    :aria-label="`平台对象数量：用户${stats.userCount}，团购${stats.itemCount}，订单${stats.orderCount}`"
  ></div>
  <p v-if="!safe" class="muted">数量超出图表可精确显示的范围，请查看上方准确数值。</p>
</template>
