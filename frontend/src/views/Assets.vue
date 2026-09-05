<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import StatCard from '../components/StatCard.vue'
import ChartBox from '../components/ChartBox.vue'
import {
  addPosition,
  assetSummary,
  createAsset,
  deleteAsset,
  deletePosition,
  listAssets,
  updateAsset,
  updatePosition,
  updatePositionPrice,
} from '../api/asset'
import { createLoan, deleteLoan, listLoans, loanPlan, updateLoan } from '../api/loan'
import { useUserStore } from '../stores/user'
import { money, date } from '../utils/format'
import { INK, SERIES_COLORS, baseAxis, itemTooltip, moneyAxisLabel } from '../utils/charts'

/**
 * 资产管理（设计文档 7.3 / 9.1）：
 * 汇总卡（总资产/总贷款/净资产）+ 类型分布、资产表格（股票基金含持仓展开）、
 * 贷款表格 + 还款计划测算（剩余本金递减曲线 + 逐期明细）
 */
const store = useUserStore()

const ASSET_TYPES = ['房产', '股票基金', '存款', '汽车', '其他']
/** 类型 → 固定色（颜色跟随实体，不随筛选重排） */
const TYPE_COLOR = {
  房产: SERIES_COLORS[0],
  股票基金: SERIES_COLORS[2],
  存款: SERIES_COLORS[5],
  汽车: SERIES_COLORS[3],
  其他: INK.muted,
}
const REPAY_LABELS = { equal_installment: '等额本息', equal_principal: '等额本金' }

const loading = ref(false)
const summary = ref({})
const assets = ref([])
const loans = ref([])
const byTypeOption = ref({})
const byTypeEmpty = ref(false)

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [sum, as, ls] = await Promise.all([assetSummary(), listAssets(), listLoans()])
    summary.value = sum
    assets.value = as
    loans.value = ls
    buildByType(sum.byType)
  } finally {
    loading.value = false
  }
}

function buildByType(byType) {
  const items = (byType || []).filter((t) => Number(t.total) > 0)
  byTypeEmpty.value = items.length === 0
  byTypeOption.value = {
    tooltip: {
      ...itemTooltip(),
      formatter: (p) => `${p.marker}${p.name}：¥${money(p.value)}（${p.data.count} 笔，占 ${p.percent}%）`,
    },
    legend: { top: 0, right: 4, itemWidth: 14, itemHeight: 8, textStyle: { color: INK.secondary, fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: ['46%', '72%'],
        center: ['42%', '55%'],
        padAngle: 2,
        itemStyle: { borderColor: '#fcfcfb', borderWidth: 2 },
        label: { color: INK.secondary, fontSize: 12, formatter: (p) => `${p.name}\n${p.percent}%` },
        labelLine: { length: 10, length2: 8, lineStyle: { color: INK.baseline } },
        data: items.map((t) => ({
          name: t.name,
          value: Number(t.total),
          count: t.count,
          itemStyle: { color: TYPE_COLOR[t.name] || SERIES_COLORS[1] },
        })),
      },
    ],
  }
}

function typeTag(type) {
  if (type === '房产') return 'warning'
  if (type === '股票基金') return 'success'
  if (type === '存款') return 'primary'
  return 'info'
}

/* ---------- 资产 ---------- */

const assetDialog = ref(false)
const assetSaving = ref(false)
const editingAssetId = ref(null)
const assetFormRef = ref(null)
const assetForm = reactive({ name: '', assetType: '房产', value: 0, purchaseDate: null, note: '' })

const assetRules = {
  name: [{ required: true, message: '请输入资产名称', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择资产类型', trigger: 'change' }],
}

function openAssetCreate() {
  editingAssetId.value = null
  Object.assign(assetForm, { name: '', assetType: '房产', value: 0, purchaseDate: null, note: '' })
  assetDialog.value = true
}

function openAssetEdit(row) {
  editingAssetId.value = row.id
  Object.assign(assetForm, {
    name: row.name,
    assetType: row.assetType,
    value: Number(row.value),
    purchaseDate: row.purchaseDate || null,
    note: row.note || '',
  })
  assetDialog.value = true
}

async function saveAsset() {
  await assetFormRef.value.validate()
  assetSaving.value = true
  try {
    const payload = {
      name: assetForm.name,
      assetType: assetForm.assetType,
      value: assetForm.value,
      purchaseDate: assetForm.purchaseDate || null,
      note: assetForm.note || null,
    }
    if (editingAssetId.value) {
      await updateAsset(editingAssetId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createAsset(payload)
      ElMessage.success('已新增')
    }
    assetDialog.value = false
    loadAll()
  } finally {
    assetSaving.value = false
  }
}

async function onDeleteAsset(row) {
  await ElMessageBox.confirm(`确定删除资产「${row.name}」吗？股票基金类资产的持仓将一并删除。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteAsset(row.id)
  ElMessage.success('已删除')
  loadAll()
}

/* ---------- 持仓（仅股票基金类） ---------- */

const positionDialog = ref(false)
const positionSaving = ref(false)
const positionAssetId = ref(null)
const editingPositionId = ref(null)
const positionFormRef = ref(null)
const positionForm = reactive({ code: '', name: '', shares: 1, costPrice: null, currentPrice: null })

const positionRules = {
  shares: [{ required: true, message: '请输入持仓数量', trigger: 'blur' }],
  currentPrice: [{ required: true, message: '请输入现价', trigger: 'blur' }],
}

function openPositionCreate(assetId) {
  positionAssetId.value = assetId
  editingPositionId.value = null
  Object.assign(positionForm, { code: '', name: '', shares: 1, costPrice: null, currentPrice: null })
  positionDialog.value = true
}

function openPositionEdit(assetId, row) {
  positionAssetId.value = assetId
  editingPositionId.value = row.id
  Object.assign(positionForm, {
    code: row.code || '',
    name: row.name || '',
    shares: row.shares,
    costPrice: row.costPrice === null ? null : Number(row.costPrice),
    currentPrice: row.currentPrice === null ? null : Number(row.currentPrice),
  })
  positionDialog.value = true
}

async function savePosition() {
  await positionFormRef.value.validate()
  positionSaving.value = true
  try {
    const payload = {
      code: positionForm.code || null,
      name: positionForm.name || null,
      shares: positionForm.shares,
      costPrice: positionForm.costPrice,
      currentPrice: positionForm.currentPrice,
    }
    if (editingPositionId.value) {
      await updatePosition(editingPositionId.value, payload)
      ElMessage.success('已更新')
    } else {
      await addPosition(positionAssetId.value, payload)
      ElMessage.success('已新增持仓')
    }
    positionDialog.value = false
    loadAll()
  } finally {
    positionSaving.value = false
  }
}

async function onDeletePosition(assetId, row) {
  await ElMessageBox.confirm(`确定删除持仓「${row.name || row.code}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deletePosition(row.id)
  ElMessage.success('已删除')
  loadAll()
}

/* ---------- 更新现价 ---------- */

const priceDialog = ref(false)
const priceSaving = ref(false)
const pricePosition = ref(null)
const priceForm = reactive({ currentPrice: null })

function openPrice(row) {
  pricePosition.value = row
  priceForm.currentPrice = row.currentPrice === null ? null : Number(row.currentPrice)
  priceDialog.value = true
}

async function savePrice() {
  if (priceForm.currentPrice === null || Number(priceForm.currentPrice) < 0) {
    ElMessage.error('请输入现价')
    return
  }
  priceSaving.value = true
  try {
    await updatePositionPrice(pricePosition.value.id, priceForm.currentPrice)
    ElMessage.success('现价已更新，估值已重算')
    priceDialog.value = false
    loadAll()
  } finally {
    priceSaving.value = false
  }
}

/* ---------- 贷款 ---------- */

const loanDialog = ref(false)
const loanSaving = ref(false)
const editingLoanId = ref(null)
const loanFormRef = ref(null)
const loanForm = reactive({
  name: '',
  principal: null,
  annualRatePct: 3.8,
  termMonths: 360,
  startDate: null,
  repaymentType: 'equal_installment',
  remainingPrincipal: null,
  lender: '',
})

const loanRules = {
  name: [{ required: true, message: '请输入贷款名称', trigger: 'blur' }],
  principal: [{ required: true, message: '请输入本金', trigger: 'blur' }],
  termMonths: [{ required: true, message: '请输入期数', trigger: 'blur' }],
}

function openLoanCreate() {
  editingLoanId.value = null
  Object.assign(loanForm, {
    name: '',
    principal: null,
    annualRatePct: 3.8,
    termMonths: 360,
    startDate: null,
    repaymentType: 'equal_installment',
    remainingPrincipal: null,
    lender: '',
  })
  loanDialog.value = true
}

function openLoanEdit(row) {
  editingLoanId.value = row.id
  Object.assign(loanForm, {
    name: row.name,
    principal: Number(row.principal),
    annualRatePct: row.annualRate === null ? 0 : Number(row.annualRate) * 100,
    termMonths: row.termMonths,
    startDate: row.startDate || null,
    repaymentType: row.repaymentType,
    remainingPrincipal: row.remainingPrincipal === null ? null : Number(row.remainingPrincipal),
    lender: row.lender || '',
  })
  loanDialog.value = true
}

async function saveLoan() {
  await loanFormRef.value.validate()
  loanSaving.value = true
  try {
    const payload = {
      name: loanForm.name,
      principal: loanForm.principal,
      annualRate: loanForm.annualRatePct / 100,
      termMonths: loanForm.termMonths,
      startDate: loanForm.startDate || null,
      repaymentType: loanForm.repaymentType,
      remainingPrincipal: loanForm.remainingPrincipal,
      lender: loanForm.lender || null,
    }
    if (editingLoanId.value) {
      await updateLoan(editingLoanId.value, payload)
      ElMessage.success('已更新，月供已重新测算')
    } else {
      await createLoan(payload)
      ElMessage.success('已新增，月供自动测算完成')
    }
    loanDialog.value = false
    loadAll()
  } finally {
    loanSaving.value = false
  }
}

async function onDeleteLoan(row) {
  await ElMessageBox.confirm(`确定删除贷款「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteLoan(row.id)
  ElMessage.success('已删除')
  loadAll()
}

/* ---------- 还款计划测算 ---------- */

const planDialog = ref(false)
const planLoading = ref(false)
const plan = ref(null)
const planOption = ref({})
const planEmpty = ref(false)

async function openPlan(row) {
  planDialog.value = true
  planLoading.value = true
  try {
    const data = await loanPlan(row.id)
    plan.value = { ...data, loanName: row.name }
    buildPlanChart(data)
  } finally {
    planLoading.value = false
  }
}

function buildPlanChart(data) {
  planEmpty.value = !data.items?.length
  const sample = data.items || []
  // 期数多时抽稀，保证折线清晰（≤60 期全画，否则每 6 期取一点，末尾保留）
  const step = Math.max(1, Math.ceil(sample.length / 60))
  const points = sample.filter((_, i) => i % step === 0 || i === sample.length - 1)
  planOption.value = {
    tooltip: {
      ...itemTooltip(),
      formatter: (p) => `第 ${p.data.period} 期<br/>剩余本金：¥${money(p.data.value)}`,
    },
    ...baseAxis(),
    xAxis: { ...baseAxis().xAxis, name: '期数', nameTextStyle: { color: INK.muted }, data: points.map((p) => p.period) },
    yAxis: { ...baseAxis().yAxis, axisLabel: moneyAxisLabel() },
    series: [
      {
        name: '剩余本金',
        type: 'line',
        data: points.map((p) => ({ period: p.period, value: Number(p.remainingPrincipal) })),
        smooth: 0.2,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, color: SERIES_COLORS[0], cap: 'round' },
        itemStyle: { color: SERIES_COLORS[0], borderColor: '#fcfcfb', borderWidth: 2 },
        areaStyle: {
          opacity: 0.12,
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: SERIES_COLORS[0] },
              { offset: 1, color: 'rgba(255,255,255,0)' },
            ],
          },
        },
        label: {
          show: true,
          position: 'top',
          color: INK.secondary,
          fontSize: 12,
          formatter: (p) => (p.dataIndex === points.length - 1 ? '剩余本金' : ''),
        },
      },
    ],
  }
}

const planSummary = computed(() => {
  if (!plan.value) return null
  return [
    { label: '月供（首月）', value: `¥${money(plan.value.monthlyPayment)}` },
    { label: '利息合计', value: `¥${money(plan.value.totalInterest)}` },
    { label: '本息合计', value: `¥${money(plan.value.totalPayment)}` },
  ]
})
</script>

<template>
  <div v-loading="loading">
    <!-- 汇总：总资产/总贷款/净资产 + 类型分布 -->
    <el-row :gutter="12">
      <el-col :span="6"><StatCard title="总资产" :value="summary.totalAssets || 0" /></el-col>
      <el-col :span="6"><StatCard title="总贷款" :value="summary.totalLoans || 0" /></el-col>
      <el-col :span="6"><StatCard title="净资产（资产 − 贷款）" :value="summary.netAssets || 0" /></el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <template #header><span class="card-title">资产类型分布</span></template>
          <ChartBox :option="byTypeOption" :empty="byTypeEmpty" height="220px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 资产表格（股票基金可展开持仓） -->
    <el-card shadow="never" class="row-gap">
      <template #header>
        <div class="card-head">
          <span class="card-title">资产列表</span>
          <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openAssetCreate">新增资产</el-button>
        </div>
      </template>
      <el-table :data="assets" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div v-if="row.assetType === '股票基金'" class="positions-panel">
              <div class="positions-head">
                <span>持仓明细（市值合计 ¥{{ money(row.marketValue) }}，估值随现价自动重算）</span>
                <el-button v-if="store.isAdmin" link type="primary" :icon="Plus" @click="openPositionCreate(row.id)">
                  新增持仓
                </el-button>
              </div>
              <el-table :data="row.positions" size="small">
                <el-table-column prop="code" label="代码" width="90" />
                <el-table-column prop="name" label="名称" min-width="110" />
                <el-table-column prop="shares" label="数量" width="90" align="right" class-name="num-cell" />
                <el-table-column label="成本价" width="100" align="right">
                  <template #default="{ row: p }">{{ p.costPrice ?? '--' }}</template>
                </el-table-column>
                <el-table-column label="现价" width="100" align="right">
                  <template #default="{ row: p }">{{ p.currentPrice ?? '--' }}</template>
                </el-table-column>
                <el-table-column label="市值" width="120" align="right">
                  <template #default="{ row: p }">
                    <span class="num-cell">{{ money((p.currentPrice ?? p.costPrice ?? 0) * p.shares) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="170" v-if="store.isAdmin">
                  <template #default="{ row: p }">
                    <el-button link type="primary" size="small" @click="openPrice(p)">更新现价</el-button>
                    <el-button link type="primary" size="small" @click="openPositionEdit(row.id, p)">编辑</el-button>
                    <el-button link type="danger" size="small" @click="onDeletePosition(row.id, p)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.assetType)" size="small">{{ row.assetType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="估值" width="140" align="right">
          <template #default="{ row }">
            <span class="num-cell">¥{{ money(row.value) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="购置日期" width="110">
          <template #default="{ row }">{{ date(row.purchaseDate) }}</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="120" v-if="store.isAdmin">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openAssetEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDeleteAsset(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 贷款表格 -->
    <el-card shadow="never" class="row-gap">
      <template #header>
        <div class="card-head">
          <span class="card-title">贷款列表</span>
          <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openLoanCreate">新增贷款</el-button>
        </div>
      </template>
      <el-table :data="loans" stripe>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="本金" width="140" align="right">
          <template #default="{ row }"><span class="num-cell">¥{{ money(row.principal) }}</span></template>
        </el-table-column>
        <el-table-column label="年利率" width="90" align="right">
          <template #default="{ row }">{{ row.annualRate === null ? '--' : (Number(row.annualRate) * 100).toFixed(2) + '%' }}</template>
        </el-table-column>
        <el-table-column prop="termMonths" label="期数(月)" width="90" align="right" />
        <el-table-column label="还款方式" width="100">
          <template #default="{ row }">{{ REPAY_LABELS[row.repaymentType] || row.repaymentType }}</template>
        </el-table-column>
        <el-table-column label="月供" width="130" align="right">
          <template #default="{ row }">
            <span class="num-cell">¥{{ money(row.monthlyPayment) }}</span>
            <span v-if="row.repaymentType === 'equal_principal'" class="desc-note">(首月)</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余本金" width="140" align="right">
          <template #default="{ row }"><span class="num-cell">¥{{ money(row.remainingPrincipal) }}</span></template>
        </el-table-column>
        <el-table-column prop="lender" label="机构" min-width="110" show-overflow-tooltip />
        <el-table-column label="操作" width="190" v-if="store.isAdmin">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPlan(row)">还款计划</el-button>
            <el-button link type="primary" size="small" @click="openLoanEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDeleteLoan(row)">删除</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" v-else>
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPlan(row)">还款计划</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 资产弹窗 -->
    <el-dialog v-model="assetDialog" :title="editingAssetId ? '编辑资产' : '新增资产'" width="480px" destroy-on-close>
      <el-form ref="assetFormRef" :model="assetForm" :rules="assetRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="assetForm.name" placeholder="如：朝阳区望京两居室" maxlength="100" />
        </el-form-item>
        <el-form-item label="类型" prop="assetType">
          <el-select v-model="assetForm.assetType" :disabled="!!editingAssetId" style="width: 100%">
            <el-option v-for="t in ASSET_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
          <div v-if="editingAssetId" class="desc-note">v1 不支持修改资产类型，如需变更请删除重建</div>
        </el-form-item>
        <el-form-item label="估值">
          <el-input-number
            v-model="assetForm.value"
            :min="0"
            :precision="2"
            :step="10000"
            controls-position="right"
            style="width: 100%"
            :disabled="assetForm.assetType === '股票基金'"
          />
          <div v-if="assetForm.assetType === '股票基金'" class="desc-note">股票基金估值由持仓自动汇总，无需手工填写</div>
        </el-form-item>
        <el-form-item label="购置日期">
          <el-date-picker v-model="assetForm.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assetForm.note" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assetDialog = false">取消</el-button>
        <el-button type="primary" :loading="assetSaving" @click="saveAsset">保存</el-button>
      </template>
    </el-dialog>

    <!-- 持仓弹窗 -->
    <el-dialog v-model="positionDialog" :title="editingPositionId ? '编辑持仓' : '新增持仓'" width="440px" destroy-on-close>
      <el-form ref="positionFormRef" :model="positionForm" :rules="positionRules" label-width="80px">
        <el-form-item label="代码">
          <el-input v-model="positionForm.code" placeholder="如：600519（选填）" maxlength="20" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="positionForm.name" placeholder="如：贵州茅台（选填）" maxlength="50" />
        </el-form-item>
        <el-form-item label="数量" prop="shares">
          <el-input-number v-model="positionForm.shares" :min="1" :precision="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成本价">
          <el-input-number v-model="positionForm.costPrice" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="现价" prop="currentPrice">
          <el-input-number v-model="positionForm.currentPrice" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="positionDialog = false">取消</el-button>
        <el-button type="primary" :loading="positionSaving" @click="savePosition">保存</el-button>
      </template>
    </el-dialog>

    <!-- 更新现价弹窗 -->
    <el-dialog v-model="priceDialog" title="更新现价" width="360px">
      <div class="price-row">
        <span>{{ pricePosition?.name || pricePosition?.code }}</span>
        <el-input-number v-model="priceForm.currentPrice" :min="0" :precision="4" :step="0.1" controls-position="right" />
      </div>
      <div class="desc-note">保存后资产估值自动重算</div>
      <template #footer>
        <el-button @click="priceDialog = false">取消</el-button>
        <el-button type="primary" :loading="priceSaving" @click="savePrice">保存</el-button>
      </template>
    </el-dialog>

    <!-- 贷款弹窗 -->
    <el-dialog v-model="loanDialog" :title="editingLoanId ? '编辑贷款' : '新增贷款'" width="480px" destroy-on-close>
      <el-form ref="loanFormRef" :model="loanForm" :rules="loanRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="loanForm.name" placeholder="如：望京房贷" maxlength="50" />
        </el-form-item>
        <el-form-item label="本金" prop="principal">
          <el-input-number v-model="loanForm.principal" :min="0.01" :precision="2" :step="10000" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="年利率(%)">
          <el-input-number v-model="loanForm.annualRatePct" :min="0" :max="100" :precision="2" :step="0.1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="期数(月)" prop="termMonths">
          <el-input-number v-model="loanForm.termMonths" :min="1" :max="600" :precision="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="还款方式">
          <el-select v-model="loanForm.repaymentType" style="width: 100%">
            <el-option label="等额本息（月供固定）" value="equal_installment" />
            <el-option label="等额本金（逐月递减）" value="equal_principal" />
          </el-select>
        </el-form-item>
        <el-form-item label="起贷日期">
          <el-date-picker v-model="loanForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="剩余本金">
          <el-input-number v-model="loanForm.remainingPrincipal" :min="0" :precision="2" :step="10000" controls-position="right" style="width: 100%" />
          <div class="desc-note">默认与本金相同；已还部分贷款时手工填写</div>
        </el-form-item>
        <el-form-item label="机构">
          <el-input v-model="loanForm.lender" placeholder="如：建设银行（选填）" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="loanDialog = false">取消</el-button>
        <el-button type="primary" :loading="loanSaving" @click="saveLoan">保存（自动测算月供）</el-button>
      </template>
    </el-dialog>

    <!-- 还款计划弹窗 -->
    <el-dialog v-model="planDialog" :title="`还款计划 ─ ${plan?.loanName || ''}`" width="760px">
      <div v-loading="planLoading">
        <div v-if="plan" class="plan-summary">
          <div v-for="s in planSummary" :key="s.label" class="plan-summary-item">
            <div class="plan-summary-label">{{ s.label }}</div>
            <div class="plan-summary-value">{{ s.value }}</div>
          </div>
          <div class="plan-summary-item">
            <div class="plan-summary-label">还款方式</div>
            <div class="plan-summary-value">{{ REPAY_LABELS[plan.repaymentType] }}</div>
          </div>
        </div>
        <ChartBox :option="planOption" :empty="planEmpty" height="220px" />
        <el-table :data="plan?.items || []" size="small" max-height="320" stripe>
          <el-table-column prop="period" label="期次" width="80" align="right" />
          <el-table-column label="月供" width="120" align="right">
            <template #default="{ row }"><span class="num-cell">¥{{ money(row.payment) }}</span></template>
          </el-table-column>
          <el-table-column label="本金" width="120" align="right">
            <template #default="{ row }"><span class="num-cell">¥{{ money(row.principal) }}</span></template>
          </el-table-column>
          <el-table-column label="利息" width="120" align="right">
            <template #default="{ row }"><span class="num-cell">¥{{ money(row.interest) }}</span></template>
          </el-table-column>
          <el-table-column label="剩余本金" min-width="130" align="right">
            <template #default="{ row }"><span class="num-cell">¥{{ money(row.remainingPrincipal) }}</span></template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
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

.positions-panel {
  padding: 8px 16px 12px 48px;
  background: #fcfcfb;
}

.positions-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--ink-secondary);
}

.desc-note {
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.5;
}

.price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  margin-bottom: 8px;
}

.plan-summary {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.plan-summary-item {
  flex: 1;
  min-width: 120px;
  padding: 10px 14px;
  background: #fcfcfb;
  border: 1px solid rgba(11, 11, 11, 0.08);
  border-radius: 8px;
}

.plan-summary-label {
  font-size: 12px;
  color: var(--ink-muted);
}

.plan-summary-value {
  font-size: 18px;
  font-weight: 700;
  margin-top: 4px;
  color: var(--ink-primary);
}
</style>
