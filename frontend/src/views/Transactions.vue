<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createTransaction,
  deleteTransaction,
  pageTransactions,
  updateTransaction,
} from '../api/transaction'
import { categoryTree } from '../api/category'
import { useUserStore } from '../stores/user'
import { money, date } from '../utils/format'

/**
 * 收支管理（设计文档 7.3）：筛选 + 分页表格 + 弹窗录入/编辑 + 逻辑删除
 * 支持从分析页钻取进入（query 携带 categoryId / keyword）
 */
const store = useUserStore()
const route = useRoute()

const PAY_METHODS = ['支付宝', '微信', '银行卡', '现金', '其他']
const TAG_SUGGESTS = ['礼尚往来', '生日', '春节', '中秋', '医疗', '教育']

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const filters = reactive({
  page: 1,
  size: 10,
  type: null,
  categoryId: null,
  memberId: null,
  keyword: '',
  dateRange: null,
})

// 分类树（表单与筛选共用，按当前类型加载）
const treeByType = reactive({ 1: [], 2: [] })

// 录入/编辑弹窗
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({
  type: 2,
  categoryId: null,
  amount: null,
  bizDate: dayjs().format('YYYY-MM-DD'),
  memberId: null,
  merchant: '',
  region: '',
  tags: [],
  paymentMethod: '',
  note: '',
})

const rules = {
  type: [{ required: true, message: '请选择收支类型', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { pattern: /^\d+(\.\d{1,2})?$/, message: '金额最多 2 位小数且大于 0', trigger: 'blur' },
  ],
  bizDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

onMounted(async () => {
  await Promise.all([loadTree(1), loadTree(2)])
  // 钻取参数：分析页跳转携带的分类/关键词
  if (route.query.categoryId) {
    filters.categoryId = Number(route.query.categoryId)
  }
  if (route.query.keyword) {
    filters.keyword = String(route.query.keyword)
  }
  load()
})

async function loadTree(type) {
  const nodes = await categoryTree(type)
  treeByType[type] = toTreeProps(nodes)
}

/** 转 el-tree-select 数据：停用节点置灰不可选 */
function toTreeProps(nodes) {
  return (nodes || []).map((n) => ({
    value: n.id,
    label: `${n.icon || ''} ${n.name}`.trim(),
    disabled: n.status === 0,
    children: n.children?.length ? toTreeProps(n.children) : undefined,
  }))
}

async function load() {
  loading.value = true
  try {
    const params = {
      page: filters.page,
      size: filters.size,
      type: filters.type || undefined,
      categoryId: filters.categoryId || undefined,
      memberId: filters.memberId || undefined,
      keyword: filters.keyword || undefined,
      startDate: filters.dateRange?.[0],
      endDate: filters.dateRange?.[1],
    }
    const data = await pageTransactions(params)
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  load()
}

function reset() {
  Object.assign(filters, { page: 1, type: null, categoryId: null, memberId: null, keyword: '', dateRange: null })
  load()
}

/* ---------- 新增 / 编辑 ---------- */

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    type: 2,
    categoryId: null,
    amount: null,
    bizDate: dayjs().format('YYYY-MM-DD'),
    memberId: null,
    merchant: '',
    region: '',
    tags: [],
    paymentMethod: '',
    note: '',
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    type: row.type,
    categoryId: row.categoryId,
    amount: row.amount,
    bizDate: row.bizDate,
    memberId: row.memberId || null,
    merchant: row.merchant || '',
    region: row.region || '',
    tags: row.tags ? row.tags.split(/[,，]/).filter(Boolean) : [],
    paymentMethod: row.paymentMethod || '',
    note: row.note || '',
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      type: form.type,
      categoryId: form.categoryId,
      amount: form.amount,
      bizDate: form.bizDate,
      memberId: form.memberId || null,
      merchant: form.merchant || null,
      region: form.region || null,
      tags: form.tags.length ? form.tags.join(',') : null,
      paymentMethod: form.paymentMethod || null,
      note: form.note || null,
    }
    if (editingId.value) {
      await updateTransaction(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createTransaction(payload)
      ElMessage.success('已记账')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确定删除 ${date(row.bizDate)} 「${row.categoryName}」${money(row.amount)} 元的记录吗？删除后可在数据库中恢复（逻辑删除）。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteTransaction(row.id)
  ElMessage.success('已删除')
  load()
}

function typeTag(row) {
  return row.type === 1 ? 'success' : 'danger'
}
</script>

<template>
  <el-card shadow="never">
    <!-- 筛选栏 -->
    <div class="page-toolbar">
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 250px"
      />
      <el-select v-model="filters.type" placeholder="类型" clearable style="width: 90px">
        <el-option label="收入" :value="1" />
        <el-option label="支出" :value="2" />
      </el-select>
      <el-tree-select
        v-model="filters.categoryId"
        :data="treeByType[filters.type || 2]"
        :key="filters.type || 2"
        placeholder="分类"
        clearable
        check-strictly
        :render-after-expand="false"
        style="width: 150px"
      />
      <el-select v-model="filters.memberId" placeholder="成员" clearable style="width: 120px">
        <el-option v-for="m in store.memberOptions" :key="m.value" :label="m.label" :value="m.value" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="关键词：商家/备注/标签"
        clearable
        style="width: 190px"
        @keyup.enter="search"
      />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">记一笔</el-button>
    </div>

    <!-- 流水表格 -->
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="bizDate" label="日期" width="110">
        <template #default="{ row }">{{ date(row.bizDate) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="typeTag(row)" size="small">{{ row.type === 1 ? '收入' : '支出' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="categoryName" label="分类" min-width="100" show-overflow-tooltip />
      <el-table-column label="金额" width="130" align="right">
        <template #default="{ row }">
          <span :class="row.type === 1 ? 'amount-income' : 'amount-expense'" class="num-cell">
            {{ row.type === 1 ? '+' : '-' }}{{ money(row.amount) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="成员" width="90">
        <template #default="{ row }">{{ row.memberName || '家庭' }}</template>
      </el-table-column>
      <el-table-column prop="merchant" label="商家" min-width="110" show-overflow-tooltip />
      <el-table-column prop="region" label="片区" min-width="100" show-overflow-tooltip />
      <el-table-column prop="tags" label="标签" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.tags">{{ row.tags.replaceAll(',', '、') }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="paymentMethod" label="支付方式" width="90">
        <template #default="{ row }">{{ row.paymentMethod || '--' }}</template>
      </el-table-column>
      <el-table-column prop="note" label="备注" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </div>

    <!-- 录入/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑流水' : '记一笔'"
      width="560px"
      destroy-on-close
      @closed="formRef?.clearValidate()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button :value="1">收入</el-radio-button>
            <el-radio-button :value="2">支出</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :precision="2"
            :step="10"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-tree-select
            v-model="form.categoryId"
            :data="treeByType[form.type]"
            :key="form.type"
            placeholder="选择分类（叶子分类）"
            check-strictly
            :render-after-expand="false"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item label="日期" prop="bizDate">
          <el-date-picker v-model="form.bizDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="成员">
          <el-select v-model="form.memberId" placeholder="家庭整体" clearable style="width: 200px">
            <el-option v-for="m in store.memberOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="商家">
          <el-input v-model="form.merchant" placeholder="如：美团外卖 / XX超市" maxlength="100" />
        </el-form-item>
        <el-form-item label="片区">
          <el-input v-model="form.region" placeholder="如：朝阳区望京" maxlength="50" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="可多选/自建，如：礼尚往来"
            style="width: 100%"
          >
            <el-option v-for="t in TAG_SUGGESTS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="form.paymentMethod" placeholder="选填" clearable style="width: 200px">
            <el-option v-for="p in PAY_METHODS" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
