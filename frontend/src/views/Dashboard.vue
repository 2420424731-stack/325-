<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import StatCard from '../components/StatCard.vue'
import ChartBox from '../components/ChartBox.vue'
import { pageTransactions } from '../api/transaction'
import { statsCategory, statsOverview, statsTrend } from '../api/stats'
import { anomalies } from '../api/analysis'
import { useUserStore } from '../stores/user'
import { money, date, currentMonth } from '../utils/format'
import {
  SERIES_COLORS,
  TYPE_COLORS,
  INK,
  baseAxis,
  baseTooltip,
  itemTooltip,
  lineSeries,
  moneyAxisLabel,
  escHtml,
} from '../utils/charts'

/**
 * 首页仪表盘（设计文档 7.3）：
 * 本月收支结余卡片 × 3、近 6 月趋势折线、支出分类环形图、最近 10 笔流水、本月预警摘要
 */
const store = useUserStore()
const router = useRouter()

const month = currentMonth()
const loading = ref(true)
const overview = ref({})
const recent = ref([])
const warnings = ref([])

const trendOption = ref({})
const pieOption = ref({})
const trendEmpty = ref(false)
const pieEmpty = ref(false)

onMounted(async () => {
  try {
    await store.fetchContext()
    const [ov, tr, cat, tx, an] = await Promise.all([
      statsOverview({ year: dayjs().year(), month: dayjs().month() + 1 }),
      statsTrend(6),
      statsCategory({ year: dayjs().year(), month: dayjs().month() + 1, type: 2 }),
      pageTransactions({ page: 1, size: 10 }),
      anomalies(month),
    ])
    overview.value = ov
    recent.value = tx.records || []
    warnings.value = (an || []).filter((a) => a.level !== 'info').slice(0, 3)
    buildTrend(tr)
    buildPie(cat)
  } finally {
    loading.value = false
  }
})

/** 近 6 月收支趋势折线：收入蓝 slot1 / 支出橙 slot2（颜色跟随实体，不随筛选变化） */
function buildTrend(points) {
  const months = points.map((p) => p.month.slice(5) + '月')
  const income = points.map((p) => Number(p.income))
  const expense = points.map((p) => Number(p.expense))
  trendEmpty.value = points.every((p) => Number(p.income) === 0 && Number(p.expense) === 0)
  trendOption.value = {
    color: [TYPE_COLORS[1], TYPE_COLORS[2]],
    tooltip: { ...baseTooltip(), valueFormatter: (v) => `¥${money(v)}` },
    legend: { top: 0, right: 8, itemWidth: 14, itemHeight: 8, textStyle: { color: INK.secondary } },
    ...baseAxis(),
    xAxis: { ...baseAxis().xAxis, data: months },
    yAxis: { ...baseAxis().yAxis, axisLabel: moneyAxisLabel() },
    series: [
      lineSeries('收入', income, TYPE_COLORS[1], { area: true }),
      lineSeries('支出', expense, TYPE_COLORS[2], { area: true }),
    ],
  }
}

/** 支出分类环形图：Top 8 + 其他，分类色固定顺序；小扇区名称靠图例+悬浮提示 */
function buildPie(cats) {
  const items = (cats || []).slice().sort((a, b) => Number(b.total) - Number(a.total))
  pieEmpty.value = items.length === 0
  const top = items.slice(0, 8)
  const rest = items.slice(8)
  const data = top.map((c, i) => ({
    name: c.categoryName,
    value: Number(c.total),
    itemStyle: { color: SERIES_COLORS[i % SERIES_COLORS.length] },
  }))
  const restSum = rest.reduce((s, c) => s + Number(c.total), 0)
  if (rest.length) {
    data.push({
      name: '其他',
      value: restSum,
      itemStyle: { color: INK.muted },
    })
  }
  const total = items.reduce((s, c) => s + Number(c.total), 0)
  pieOption.value = {
    tooltip: {
      ...itemTooltip(),
      formatter: (p) =>
        `${p.marker}${escHtml(p.name)}：¥${money(p.value)}（${p.percent}%）`,
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 4,
      top: 'middle',
      itemWidth: 14,
      itemHeight: 8,
      textStyle: { color: INK.secondary, fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '72%'],
        center: ['38%', '50%'],
        padAngle: 2,
        itemStyle: { borderColor: '#fcfcfb', borderWidth: 2 },
        label: {
          color: INK.secondary,
          fontSize: 12,
          formatter: (p) => (p.percent >= 8 ? `${p.name}\n${p.percent}%` : ''),
        },
        labelLine: { length: 10, length2: 8, lineStyle: { color: INK.baseline } },
        data,
      },
    ],
  }
  // 供空态文案外的 tooltip 百分比换算无额外需求
  void total
}

function goTransactions() {
  router.push('/transactions')
}

/** 问候语（按时间段切换） */
const hour = new Date().getHours()
const greeting =
  (hour < 6 ? '夜深了' : hour < 12 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好') +
  `，${store.user?.nickname || store.user?.username || '朋友'}`

const monthChip = dayjs().format('YYYY年M月')
</script>

<template>
  <div v-loading="loading">
    <!-- 问候欢迎条 -->
    <div class="hello-bar">
      <div class="hello-left">
        <span class="hello-wave">👋</span>
        <div>
          <div class="hello-title">{{ greeting }}</div>
          <div class="hello-sub" v-if="store.family">
            {{ store.family.name }} · 本月收支与家庭财务概览
          </div>
        </div>
      </div>
      <div class="hello-right">
        <span class="month-chip">📅 {{ monthChip }}</span>
        <router-link to="/transactions" class="hello-btn">记一笔 →</router-link>
      </div>
    </div>

    <!-- 本月收支结余卡片 -->
    <el-row :gutter="12">
      <el-col :span="8">
        <StatCard title="本月收入" accent="income" :value="overview.income || 0" />
      </el-col>
      <el-col :span="8">
        <StatCard title="本月支出" accent="expense" :value="overview.expense || 0" />
      </el-col>
      <el-col :span="8">
        <StatCard title="本月结余" accent="balance" :value="overview.balance || 0" />
      </el-col>
    </el-row>

    <!-- 趋势 + 分类占比 -->
    <el-row :gutter="12" class="row-gap">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>近 6 个月收支趋势</span>
              <el-link type="primary" :underline="false" @click="goTransactions">查看明细</el-link>
            </div>
          </template>
          <ChartBox :option="trendOption" :empty="trendEmpty" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>本月支出分类占比</span>
              <el-link type="primary" :underline="false" @click="goTransactions">查看明细</el-link>
            </div>
          </template>
          <ChartBox :option="pieOption" :empty="pieEmpty" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近流水 + 预警摘要 -->
    <el-row :gutter="12" class="row-gap">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>最近 10 笔流水</span>
              <el-link type="primary" :underline="false" @click="goTransactions">全部流水</el-link>
            </div>
          </template>
          <el-table :data="recent" size="small">
            <el-table-column prop="bizDate" label="日期" width="110">
              <template #default="{ row }">{{ date(row.bizDate) }}</template>
            </el-table-column>
            <el-table-column prop="categoryName" label="分类" min-width="110" show-overflow-tooltip />
            <el-table-column label="金额" width="130" align="right">
              <template #default="{ row }">
                <span :class="row.type === 1 ? 'amount-income' : 'amount-expense'" class="num-cell">
                  {{ row.type === 1 ? '+' : '-' }}{{ money(row.amount) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="经手" width="90">
              <template #default="{ row }">{{ row.memberName || '家庭' }}</template>
            </el-table-column>
            <el-table-column prop="merchant" label="商家" min-width="110" show-overflow-tooltip />
            <el-table-column prop="note" label="备注" min-width="90" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>本月预警摘要</span>
              <el-link type="primary" :underline="false" @click="router.push('/analysis')">查看分析</el-link>
            </div>
          </template>
          <el-empty v-if="!warnings.length" description="本月暂无预警 🎉" :image-size="64" />
          <div v-else class="warn-list">
            <div v-for="w in warnings" :key="w.ruleCode + w.dimension" class="warn-item">
              <span class="warn-dot" :class="w.level === 'danger' ? 'dot-danger' : 'dot-warning'"></span>
              <div class="warn-body">
                <div class="warn-title">{{ w.title }}</div>
                <div class="warn-desc">{{ w.description }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.row-gap {
  margin-top: 12px;
}

/* 问候欢迎条 */
.hello-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
  padding: 20px 24px;
  border-radius: 16px;
  background:
    radial-gradient(420px 130px at 90% -40%, rgba(246, 196, 83, 0.16), transparent 70%),
    linear-gradient(105deg, #ffffff 30%, #e8f4ec 100%);
  border: 1px solid #ddebe1;
  box-shadow: 0 10px 26px -16px rgba(23, 74, 46, 0.2);
}

.hello-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hello-wave {
  font-size: 30px;
  line-height: 1;
}

.hello-title {
  font-size: 20px;
  font-weight: 800;
  color: #143d26;
  letter-spacing: 1px;
}

.hello-sub {
  font-size: 12.5px;
  color: #6b8f78;
  margin-top: 4px;
}

.hello-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.month-chip {
  font-size: 13px;
  font-weight: 600;
  color: #1b623c;
  background: #ffffff;
  border: 1px solid #cfe2d4;
  padding: 6px 14px;
  border-radius: 14px;
}

.hello-btn {
  font-size: 13px;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(180deg, #1b623c, #123f28);
  padding: 7px 18px;
  border-radius: 14px;
  text-decoration: none;
  box-shadow: 0 6px 14px -6px rgba(18, 70, 43, 0.5);
  transition: transform 0.15s, filter 0.15s;
}

.hello-btn:hover {
  filter: brightness(1.1);
  transform: translateY(-1px);
}

/* 卡片头部：标题前绿色竖标 */
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  color: #1c3a29;
}

.card-head > span:first-child {
  display: inline-flex;
  align-items: center;
}

.card-head > span:first-child::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  border-radius: 3px;
  background: linear-gradient(180deg, #57a276, #1b623c);
  margin-right: 8px;
}

.card-head :deep(.el-link) {
  font-size: 12.5px;
}

.warn-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(11, 11, 11, 0.06);
}

.warn-item:last-child {
  border-bottom: none;
}

.warn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.dot-danger {
  background: var(--st-critical);
}

.dot-warning {
  background: var(--st-warning);
}

.warn-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-primary);
}

.warn-desc {
  font-size: 12px;
  color: var(--ink-secondary);
  margin-top: 2px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
