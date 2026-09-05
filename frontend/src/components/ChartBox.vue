<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 容器封装（设计文档 7.4 ChartBox）：
 * 统一 loading、空数据占位、resize 与卸载 dispose
 */
const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
  loading: { type: Boolean, default: false },
  /** 图表数据是否为空（空则显示占位文案） */
  empty: { type: Boolean, default: false },
})

const el = ref(null)
let chart = null

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})

watch(() => props.option, render, { deep: true })
watch(() => props.loading, (v) => (v ? chart?.showLoading() : chart?.hideLoading()))
watch(() => props.empty, (v) => (v ? chart?.clear() : render()))

function onResize() {
  chart?.resize()
}

function render() {
  if (!chart || props.empty) return
  chart.setOption(props.option, { notMerge: true })
  chart.hideLoading()
}
</script>

<template>
  <div class="chart-wrap" :style="{ height }">
    <div ref="el" class="chart-el"></div>
    <div v-if="empty" class="chart-empty">
      <el-empty description="暂无数据" :image-size="72" />
    </div>
  </div>
</template>

<style scoped>
.chart-wrap {
  position: relative;
  width: 100%;
}

.chart-el {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fcfcfb;
}
</style>
