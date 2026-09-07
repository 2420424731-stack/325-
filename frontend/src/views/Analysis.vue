<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StatCard from '../components/StatCard.vue'
import ChartBox from '../components/ChartBox.vue'
import { anomalies, compare, report } from '../api/analysis'
import { statsCategory, statsMerchant, statsRegion, statsTrend } from '../api/stats'
import { currentMonth, money, pct } from '../utils/format'
import {
  SERIES_COLORS,
  TYPE_COLORS,
  INK,
  baseAxis,
  baseTooltip,
  itemTooltip,
  lineSeries,
  moneyAxisLabel,
} from '../utils/charts'

/**
 * 统计分析（设计文档 7.3 / 8.1-8.2）：
 * 环比同比指标卡、收支趋势、分类柱状图、商家 Top N、片区分布、
 * 异常预警列表（R1~R7，可钻取流水）、月度分析报告
 */
const router = useRouter()

const month = ref(currentMonth())
const loading = ref(true)
const cmp = ref(null)
const anomalyList = ref([])
const reportText = ref('')

const trendOption = ref({})
const trendEmpty = ref(false)
const barOption = ref({})
const barEmpty = ref(false)
const merchantOption = ref({})
const merchantEmpty = ref(false)
const regionOption = ref({})
const regionEmpty = ref(false)

const LEVEL_META = {
  danger: { label: '严重', color: '#d03b3b', icon: '⛔' },
  warning: { label: '提示', color: '#fab219', icon: '⚠️' },
  info: { label: '参考', color: '#898781', icon: '💡' },
}

const incomeChips = computed(() =>
  cmp.value
    ? [
        { label: '环比', value: cmp.value.income.momPct },
        { label: '同比', value: cmp.value.income.yoyPct },
      ]
    : [],
)
const expenseChips = computed(() =>
  cmp.value
    ? [
        { label: '环比', value: cmp.value.expense.momPct },
        { label: '同比', value: cmp.value.expense.yoyPct },
      ]
    : [],
)
const balanceChips = computed(() =>
  cmp.value
    ? [
        { label: '环比', value: cmp.value.balance.momPct },
        { label: '同比', value: cmp.value.balance.yoyPct },
      ]
    : [],
)

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const ym = month.value
    const [c, an, rp, tr, cat, merch, region] = await Promise.all([
      compare(ym),
      anomalies(ym),
      report(ym),
      statsTrend(12),
      statsCategory({ month: ym, type: 2 }),
      statsMerchant({ month: ym, type: 2, topN: 10 }),
      statsRegion({ month: ym, type: 2 }),
    ])
    cmp.value = c
    anomalyList.value = an || []
    reportText.value = rp?.text || ''
    buildTrend(tr)
    buildCategoryBar(cat)
    buildMerchant(merch)
    buildRegion(region)
  } finally {
    loading.value = false
  }
}

function buildTrend(points) {
  trendEmpty.value = points.every((p) => Number(p.income) === 0 && Number(p.expense) === 0)
  trendOption.value = {
    color: [TYPE_COLORS[1], TYPE_COLORS[2]],
    tooltip: { ...baseTooltip(), valueFormatter: (v) => `¥${money(v)}` },
    legend: { top: 0, right: 8, itemWidth: 14, itemHeight: 8, textStyle: { color: INK.secondary } },
    ...baseAxis(),
    xAxis: { ...baseAxis().xAxis, data: points.map((p) => p.month.slice(5) + '月') },
    yAxis: { ...baseAxis().yAxis, axisLabel: moneyAxisLabel() },
    series: [
      lineSeries('收入', points.map((p) => Number(p.income)), TYPE_COLORS[1], { area: true }),
      lineSeries('支出', points.map((p) => Number(p.expense)), TYPE_COLORS[2], { area: true }),
    ],
  }
}

/** 分类柱状图：Top 8，分类色固定顺序 */
function buildCategoryBar(cats) {
  const items = (cats || []).slice().sort((a, b) => Number(b.total) - Number(a.total)).slice(0, 8)
  barEmpty.value = items.length === 0
  barOption.value = {
    tooltip: {
      ...itemTooltip(),
      formatter: (p) => `${p.marker}${p.name}：¥${money(p.value)}（${p.data.count} 笔）`,
    },
    ...baseAxis(),
    xAxis: {
      ...baseAxis().xAxis,
      data: items.map((c) => c.categoryName),
      axisLabel: { color: INK.muted, interval: 0, fontSize: 11, rotate: items.length > 6 ? 25 : 0 },
    },
    yAxis: { ...baseAxis().yAxis, axisLabel: moneyAxisLabel() },
    series: [
      {
        type: 'bar',
        barWidth: '52%',
        data: items.map((c, i) => ({
          name: c.categoryName,
          value: Number(c.total),
          count: c.count,
          itemStyle: {
            color: SERIES_COLORS[i % SERIES_COLORS.length],
            borderRadius: [4, 4, 0, 0],
          },
        })),
        emphasis: { focus: 'series' },
      },
    ],
  }
}

/** 商家 Top N：横向条形，单序列 slot1 蓝 */
function buildMerchant(items) {
  const top = (items || []).slice(0, 10).reverse() // 反转让最大在顶部
  merchantEmpty.value = top.length === 0
  merchantOption.value = {
    tooltip: {
      ...itemTooltip(),
      formatter: (p) => `${p.marker}${p.name}：¥${money(p.value)}（${p.data.count} 笔）`,
    },
    grid: { left: 8, right: 40, top: 8, bottom: 4, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: INK.grid } }, axisLabel: { color: INK.muted } },
    yAxis: {
      type: 'category',
      data: top.map((m) => m.name),
      axisLine: { lineStyle: { color: INK.baseline } },
      axisTick: { show: false },
      axisLabel: { color: INK.secondary, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        barWidth: '60%',
        data: top.map((m) => ({
          name: m.name,
          value: Number(m.total),
          count: m.count,
          itemStyle: { color: SERIES_COLORS[0], borderRadius: [0, 4, 4, 0] },
        })),
        label: {
          show: true,
          position: 'right',
          color: INK.secondary,
          fontSize: 11,
          formatter: (p) => (Number(p.value) >= 1000 ? `${(p.value / 1000).toFixed(1)}k` : p.value),
        },
      },
    ],
  }
}

/** 片区分布：横向条形，单序列 slot3 青（颜色跟随实体） */
function buildRegion(items) {
  const top = (items || []).slice().sort((a, b) => Number(b.total) - Number(a.total)).slice(0, 8).reverse()
  regionEmpty.value = top.length === 0
  regionOption.value = {
    tooltip: {
      ...itemTooltip(),
      formatter: (p) => `${p.marker}${p.name}：¥${money(p.value)}（${p.data.count} 笔）`,
    },
    grid: { left: 8, right: 40, top: 8, bottom: 4, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: INK.grid } }, axisLabel: { color: INK.muted } },
    yAxis: {
      type: 'category',
      data: top.map((r) => r.name),
      axisLine: { lineStyle: { color: INK.baseline } },
      axisTick: { show: false },
      axisLabel: { color: INK.secondary, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        barWidth: '60%',
        data: top.map((r) => ({
          name: r.name,
          value: Number(r.total),
          count: r.count,
          itemStyle: { color: SERIES_COLORS[2], borderRadius: [0, 4, 4, 0] },
        })),
        label: {
          show: true,
          position: 'right',
          color: INK.secondary,
          fontSize: 11,
          formatter: (p) => (Number(p.value) >= 1000 ? `${(p.value / 1000).toFixed(1)}k` : p.value),
        },
      },
    ],
  }
}

/** 钻取到流水列表（设计文档 8.3：所有分析数据一键钻取） */
function drill(item) {
  if (item.drillCategoryId) {
    router.push({ path: '/transactions', query: { categoryId: item.drillCategoryId } })
  } else if (item.drillTag) {
    router.push({ path: '/transactions', query: { keyword: item.drillTag } })
  }
}
</script>

<template>
  <div v-loading="loading">
    <!-- 月份切换 -->
    <div class="page-toolbar">
      <el-date-picker
        v-model="month"
        type="month"
        value-format="YYYY-MM"
        placeholder="选择月份"
        :clearable="false"
        style="width: 140px"
        @change="loadAll"
      />
      <span class="hint">统计=客观汇总；分析=发现问题并解释（规则 R1~R7）</span>
    </div>

    <!-- 环比/同比指标卡 -->
    <el-row :gutter="12">
      <el-col :span="8">
        <StatCard title="本月收入" :value="cmp?.income.current || 0" :chips="incomeChips" />
      </el-col>
      <el-col :span="8">
        <StatCard title="本月支出" :value="cmp?.expense.current || 0" :chips="expenseChips" invert />
      </el-col>
      <el-col :span="8">
        <StatCard title="本月结余" :value="cmp?.balance.current || 0" :chips="balanceChips" />
      </el-col>
    </el-row>
    <div v-if="cmp?.conclusion" class="conclusion">
      {{ month }} 概览：{{ cmp.conclusion }}
    </div>

    <!-- 趋势 + 分类柱状图 -->
    <el-row :gutter="12" class="row-gap">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">近 12 个月收支趋势</span></template>
          <ChartBox :option="trendOption" :empty="trendEmpty" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">本月支出分类 Top 8</span></template>
          <ChartBox :option="barOption" :empty="barEmpty" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 商家 Top N + 片区 -->
    <el-row :gutter="12" class="row-gap">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">商家 Top 10</span></template>
          <ChartBox :option="merchantOption" :empty="merchantEmpty" height="320px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">消费片区分布 Top 8</span></template>
          <ChartBox :option="regionOption" :empty="regionEmpty" height="320px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警 + 报告 -->
    <el-row :gutter="12" class="row-gap">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="card-title">预警与关注项</span></template>
          <el-empty v-if="!anomalyList.length" description="本月没有预警与关注项" :image-size="64" />
          <div v-else class="anomaly-list">
            <div
              v-for="a in anomalyList"
              :key="a.ruleCode + a.dimension"
              class="anomaly-item"
              :class="{ drillable: a.drillCategoryId || a.drillTag }"
              @click="drill(a)"
            >
              <div class="anomaly-head">
                <span class="anomaly-badge" :style="{ background: LEVEL_META[a.level].color }">
                  {{ LEVEL_META[a.level].icon }} {{ LEVEL_META[a.level].label }}
                </span>
                <span class="anomaly-title">{{ a.title }}</span>
                <span v-if="a.exceedPct !== null && a.exceedPct !== undefined" class="anomaly-pct">
                  +{{ pct(a.exceedPct) }}
                </span>
              </div>
              <div class="anomaly-desc">{{ a.description }}</div>
              <div v-if="a.suggestion" class="anomaly-suggest">💡 {{ a.suggestion }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">月度分析报告</span>
              <el-button link type="primary" @click="loadAll">重新生成</el-button>
            </div>
          </template>
          <div v-if="reportText" class="report-text">{{ reportText }}</div>
          <el-empty v-else description="暂无报告" :image-size="64" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--ink-muted);
}

.conclusion {
  margin-top: 12px;
  padding: 10px 14px;
  background: #fcfcfb;
  border: 1px solid rgba(11, 11, 11, 0.1);
  border-left: 3px solid var(--series-1);
  border-radius: 6px;
  font-size: 13px;
  color: var(--ink-secondary);
}

.row-gap {
  margin-top: 12px;
}

.card-title {
  font-weight: 600;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.anomaly-item {
  padding: 10px 12px;
  border: 1px solid rgba(11, 11, 11, 0.06);
  border-radius: 8px;
  margin-bottom: 8px;
  background: #fcfcfb;
}

.anomaly-item.drillable {
  cursor: pointer;
}

.anomaly-item.drillable:hover {
  border-color: var(--series-1);
  box-shadow: 0 2px 8px rgba(42, 120, 214, 0.12);
}

.anomaly-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.anomaly-badge {
  font-size: 12px;
  color: #fff;
  padding: 2px 8px;
  border-radius: 10px;
  flex-shrink: 0;
}

.anomaly-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-primary);
}

.anomaly-pct {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--delta-down);
}

.anomaly-desc {
  font-size: 12px;
  color: var(--ink-secondary);
  margin-top: 6px;
  line-height: 1.6;
}

.anomaly-suggest {
  font-size: 12px;
  color: var(--ink-secondary);
  margin-top: 6px;
  background: rgba(137, 135, 129, 0.08);
  padding: 6px 8px;
  border-radius: 6px;
}

.report-text {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.8;
  color: var(--ink-primary);
  background: #fcfcfb;
  padding: 14px;
  border-radius: 8px;
  max-height: 480px;
  overflow-y: auto;
}
</style>
