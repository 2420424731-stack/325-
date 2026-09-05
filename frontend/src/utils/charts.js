/**
 * ECharts 通用样式常量（与 style.css 中 dataviz 色板一致）：
 * - 分类色固定顺序，绝不循环
 * - 墨色分层：轴/网格用 muted、hairline，文字用 ink
 * - 折线 2px、圆角数据端、≥8px 标记
 */

export const SERIES_COLORS = [
  '#2a78d6', // slot1 蓝
  '#eb6834', // slot2 橙
  '#1baf7a', // slot3 青
  '#eda100', // slot4 黄
  '#e87ba4', // slot5 粉
  '#008300', // slot6 绿
  '#4a3aa7', // slot7 紫
  '#e34948', // slot8 红
]

export const INK = {
  primary: '#0b0b0b',
  secondary: '#52514e',
  muted: '#898781',
  grid: '#e1e0d9',
  baseline: '#c3c2b7',
}

/** 收入/支出固定色（颜色跟随实体，不随筛选重排） */
export const TYPE_COLORS = {
  1: '#2a78d6', // 收入 slot1 蓝
  2: '#eb6834', // 支出 slot2 橙
}

export function baseTooltip() {
  return {
    trigger: 'axis',
    backgroundColor: '#ffffff',
    borderColor: 'rgba(11,11,11,0.10)',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: INK.primary, fontSize: 13 },
    extraCssText: 'box-shadow: 0 4px 12px rgba(0,0,0,0.08); border-radius: 6px;',
    axisPointer: { type: 'cross', lineStyle: { color: INK.baseline } },
  }
}

export function itemTooltip() {
  return {
    trigger: 'item',
    backgroundColor: '#ffffff',
    borderColor: 'rgba(11,11,11,0.10)',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: INK.primary, fontSize: 13 },
    extraCssText: 'box-shadow: 0 4px 12px rgba(0,0,0,0.08); border-radius: 6px;',
  }
}

/** 直角坐标系的默认轴/网格（隐性格线、muted 轴标签） */
export function baseAxis() {
  return {
    grid: { left: 8, right: 16, top: 40, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      axisLine: { lineStyle: { color: INK.baseline } },
      axisTick: { show: false },
      axisLabel: { color: INK.muted },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: INK.grid } },
      axisLabel: { color: INK.muted },
    },
  }
}

/** 折线系列：2px 线、圆角数据端、8px 标记、末端直接标注系列名 */
export function lineSeries(name, data, color, { area = false } = {}) {
  const s = {
    name,
    type: 'line',
    data,
    smooth: 0.35,
    symbol: 'circle',
    symbolSize: 8,
    lineStyle: { width: 2, color, cap: 'round' },
    itemStyle: { color, borderColor: '#fcfcfb', borderWidth: 2 },
    label: {
      show: true,
      position: 'top',
      color: INK.secondary,
      fontSize: 12,
      formatter: (p) => (p.dataIndex === data.length - 1 ? p.seriesName : ''),
    },
    emphasis: { focus: 'series' },
  }
  if (area) {
    s.areaStyle = {
      opacity: 0.12,
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color },
          { offset: 1, color: 'rgba(255,255,255,0)' },
        ],
      },
    }
  }
  return s
}

/** 金额轴标签（千分位） */
export function moneyAxisLabel() {
  return {
    formatter: (v) => (Math.abs(v) >= 10000 ? `${(v / 10000).toFixed(1)}万` : v),
  }
}
