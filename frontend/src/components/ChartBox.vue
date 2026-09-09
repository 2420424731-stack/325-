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

// el：模板里 ref 绑定的 DOM 节点，ECharts 将实例挂载到它上面
// chart：ECharts 实例句柄。刻意不用 ref 包一层——ECharts 实例内部结构复杂，
// 放进 Vue 响应式系统只会带来无谓的性能开销，故用普通变量 + 手动管理生命周期
const el = ref(null)
let chart = null

// 挂载完成（DOM 已存在）后初始化图表，并监听窗口尺寸变化以便自适应缩放
onMounted(() => {
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', onResize)
})

// 组件销毁前必须移除监听并 dispose 实例：
// ECharts 内部持有定时器/事件/DOM 引用，不释放会造成内存泄漏与“容器已被销毁”的报错
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})

// 深度监听父组件传入的 option（图表配置）：配置一变（如切换月份后重组数据）就整体重绘
watch(() => props.option, render, { deep: true })
// loading 为 true 时显示 ECharts 自带的旋转加载动画，请求返回后关闭
watch(() => props.loading, (v) => (v ? chart?.showLoading() : chart?.hideLoading()))
// 数据为空时清空画布让位给“暂无数据”遮罩；数据恢复后重新渲染
watch(() => props.empty, (v) => (v ? chart?.clear() : render()))

// 窗口尺寸变化时让图表按容器新尺寸重排（ECharts 自带 resize）
function onResize() {
  chart?.resize()
}

// 统一渲染入口：notMerge: true 表示整份替换配置（而非增量合并旧配置），
// 保证任何时刻画布内容与最新 option 完全一致，不会残留旧数据
function render() {
  if (!chart || props.empty) return
  chart.setOption(props.option, { notMerge: true })
  chart.hideLoading()
}
</script>

<template>
  <!-- 图表外层容器：高度由父组件通过 height prop 指定，内部相对定位供空状态遮罩居中 -->
  <div class="chart-wrap" :style="{ height }">
    <!-- ECharts 实际挂载的节点，撑满外层宽高 -->
    <div ref="el" class="chart-el"></div>
    <!-- 数据为空时显示的占位遮罩：盖在图表上方居中提示“暂无数据”（empty 由父组件判断传入） -->
    <div v-if="empty" class="chart-empty">
      <el-empty description="暂无数据" :image-size="72" />
    </div>
  </div>
</template>

<style scoped>
/* 三段式布局：wrap 为定位外层，chart-el 撑满内部，chart-empty 绝对定位铺满实现居中遮罩 */
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
