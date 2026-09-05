<script setup>
import { computed } from 'vue'
import { signedPct } from '../utils/format'

/**
 * 统计指标卡（设计文档 7.4 StatCard）：标题、数值、环比/同比箭头
 * props.chips: [{ label: '环比', value: 12.5 }, { label: '同比', value: -3.2 }]
 * 数值正增长为「好」用绿，负增长用红；支出类指标传 invert 反转语义
 */
const props = defineProps({
  title: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  prefix: { type: String, default: '¥' },
  chips: { type: Array, default: () => [] },
  invert: { type: Boolean, default: false },
})

function chipClass(v) {
  if (v === null || v === undefined) return 'chip-flat'
  const good = props.invert ? v < 0 : v > 0
  if (v === 0) return 'chip-flat'
  return good ? 'chip-up' : 'chip-down'
}

function chipArrow(v) {
  if (v === null || v === undefined || v === 0) return '—'
  return v > 0 ? '▲' : '▼'
}

const displayValue = computed(() =>
  Number(props.value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }),
)
</script>

<template>
  <div class="stat-card">
    <div class="stat-title">{{ title }}</div>
    <div class="stat-value">
      <span class="stat-prefix">{{ prefix }}</span>{{ displayValue }}
    </div>
    <div v-if="chips.length" class="stat-chips">
      <span v-for="c in chips" :key="c.label" class="stat-chip" :class="chipClass(c.value)">
        {{ c.label }} {{ chipArrow(c.value) }}{{ signedPct(c.value) }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  padding: 16px 20px;
  background: #fcfcfb;
  border: 1px solid rgba(11, 11, 11, 0.1);
  border-radius: 8px;
}

.stat-title {
  font-size: 13px;
  color: var(--ink-secondary);
  margin-bottom: 6px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ink-primary);
  letter-spacing: 0.3px;
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
  margin-top: 8px;
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
