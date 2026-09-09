<script setup>
import { computed } from 'vue'
import { signedPct } from '../utils/format'

/**
 * 统计指标卡（设计文档 7.4 StatCard）：标题、数值、环比/同比箭头
 * props.chips: [{ label: '环比', value: 12.5 }, { label: '同比', value: -3.2 }]
 * props.accent: 'income'(绿) | 'expense'(橙) | 'balance'(墨绿) | 默认墨绿
 * 数值正增长为「好」用绿，负增长用红；支出类指标传 invert 反转语义
 */
const props = defineProps({
  title: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  prefix: { type: String, default: '¥' },
  chips: { type: Array, default: () => [] },
  invert: { type: Boolean, default: false },
  accent: { type: String, default: 'balance' },
})

// 环比/同比角标配色：数值正负决定涨/跌色；支出类指标语义相反——支出上涨反而是坏消息，
// 所以由父组件传 invert 翻转“好/坏”判定（颜色仍表达好坏，而不是单纯的正负）
function chipClass(v) {
  if (v === null || v === undefined) return 'chip-flat'
  const good = props.invert ? v < 0 : v > 0
  if (v === 0) return 'chip-flat'
  return good ? 'chip-up' : 'chip-down'
}

// 环比/同比旁的箭头字符：上升 ▲、下降 ▼；数据缺失或为 0 时显示占位符「—」
function chipArrow(v) {
  if (v === null || v === undefined || v === 0) return '—'
  return v > 0 ? '▲' : '▼'
}

// 主数值展示格式：响应式计算，千分位 + 两位小数（与 utils/format.js 的 money 规则一致）
const displayValue = computed(() =>
  Number(props.value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }),
)
</script>

<template>
  <!-- 指标卡骨架：class 里的 accent-income/expense/balance 决定顶部饰条与小圆点的主题色 -->
  <div class="stat-card" :class="`accent-${accent}`">
    <!-- 顶部渐变饰条（纯装饰） -->
    <span class="stat-strip"></span>
    <!-- 指标名：带色小圆点 + 标题文字 -->
    <div class="stat-title">
      <span class="stat-dot"></span>{{ title }}
    </div>
    <!-- 主数值区：前缀符号（¥）+ 千分位格式化后的数字 -->
    <div class="stat-value">
      <span class="stat-prefix">{{ prefix }}</span>{{ displayValue }}
    </div>
    <!-- 对比角标区：环比/同比等一排小标签，无 chips 时不渲染 -->
    <div v-if="chips.length" class="stat-chips">
      <span v-for="c in chips" :key="c.label" class="stat-chip" :class="chipClass(c.value)">
        {{ c.label }} {{ chipArrow(c.value) }}{{ signedPct(c.value) }}
      </span>
    </div>
  </div>
</template>

<style scoped>
/* 卡片主体：白底 + 绿细边 + 圆角 + 柔和投影；hover 时轻微上浮增强点击感 */
.stat-card {
  position: relative;
  padding: 20px 22px 18px;
  background: #ffffff;
  border: 1px solid #e2ece4;
  border-radius: 14px;
  box-shadow: 0 8px 22px -12px rgba(23, 74, 46, 0.14);
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px -14px rgba(23, 74, 46, 0.24);
}

/* 顶部渐变饰条 */
.stat-strip {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 4px;
}

.accent-income .stat-strip {
  background: linear-gradient(90deg, #21b57d, #7fdcb4);
}

.accent-expense .stat-strip {
  background: linear-gradient(90deg, #eb6834, #f5a678);
}

.accent-balance .stat-strip {
  background: linear-gradient(90deg, #1b623c, #57a276);
}

.stat-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ink-secondary);
  margin-bottom: 8px;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1b623c;
}

.accent-income .stat-dot {
  background: #21b57d;
}

.accent-expense .stat-dot {
  background: #eb6834;
}

.stat-value {
  font-size: 27px;
  font-weight: 700;
  color: #173a26;
  letter-spacing: 0.3px;
  font-variant-numeric: tabular-nums;
}

.stat-prefix {
  font-size: 15px;
  font-weight: 500;
  color: var(--ink-muted);
  margin-right: 2px;
}

.stat-chips {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.stat-chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  line-height: 18px;
}

.chip-up {
  color: var(--delta-up);
  background: rgba(12, 163, 12, 0.1);
}

.chip-down {
  color: var(--delta-down);
  background: rgba(208, 59, 59, 0.1);
}

.chip-flat {
  color: var(--ink-muted);
  background: rgba(137, 135, 129, 0.12);
}
</style>
