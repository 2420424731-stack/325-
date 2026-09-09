<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createTransaction,
  deleteTransaction,
  exportTransactions,
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

// 下拉选项常量：支付方式枚举；标签候选词（标签还支持自由输入自建，这里只做快捷选项）
const PAY_METHODS = ['支付宝', '微信', '银行卡', '现金', '其他']
const TAG_SUGGESTS = ['礼尚往来', '生日', '春节', '中秋', '医疗', '教育']

// ===== 列表数据与筛选条件状态（分页查询共用同一份状态） =====
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const filters = reactive({
  page: 1,
  size: 10,
  type: null,
  categoryId: null,
  memberId: null,
  keyword: '', // 关键词：匹配商家/备注/标签
  dateRange: null, // 形如 [起, 止]，提交接口时拆成 startDate/endDate
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
  categoryName: '', // 分类显示（含父路径），提交时不携带
  amount: null,
  bizDate: dayjs().format('YYYY-MM-DD'),
  memberId: '',
  merchant: '',
  region: '',
  tags: [],
  paymentMethod: '',
  note: '',
})

// ===== 弹窗表单校验规则 =====
// 金额正则 ^\d+(\.\d{1,2})?$：只接受“非负整数或最多两位小数”（元/角/分），负数/0 由输入框 min 0.01 兜底
const rules = {
  type: [{ required: true, message: '请选择收支类型', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { pattern: /^\d+(\.\d{1,2})?$/, message: '金额最多 2 位小数且大于 0', trigger: 'blur' },
  ],
  bizDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

// ===== 页面初始化 =====
onMounted(async () => {
  // 并发预载 收入/支出 两棵分类树，供筛选下拉与记账弹窗共用
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

// ---------- 记账表单分类选择（弹层树：点击父分类=展开，点击叶子=选中） ----------

const catPopVisible = ref(false)

/** 从树中查分类 id 的显示路径，如 ["餐饮支出", "外卖"] */
function findPath(nodes, id, trail = []) {
  for (const n of nodes || []) {
    const cur = [...trail, n.label]
    if (n.value === id) return cur
    const hit = findPath(n.children, id, cur)
    if (hit) return hit
  }
  return null
}

/** 树节点点击：父分类展开/收起不选中；叶子分类选中并关闭弹层 */
function onCategoryClick(data, node) {
  if (data.disabled) return // 停用分类不可选
  if (!node.isLeaf) {
    node.expanded = !node.expanded
    return
  }
  form.categoryId = data.value
  form.categoryName = findPath(treeByType[form.type], data.value)?.join(' / ') || data.label
  catPopVisible.value = false
}

/** 收入/支出切换：分类树不同，清空已选分类 */
function onTypeChange() {
  form.categoryId = null
  form.categoryName = ''
}

// ===== 拉取分页列表：把当前筛选状态组装为接口参数 =====
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
      // 日期范围数组拆成起止两个参数；空值传 undefined 表示后端不过滤该条件
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

// 查询：回到第 1 页再加载（防止停留在超出范围的分页）
function search() {
  filters.page = 1
  load()
}

// 重置：一键清空所有筛选条件后重新加载
function reset() {
  Object.assign(filters, { page: 1, type: null, categoryId: null, memberId: null, keyword: '', dateRange: null })
  load()
}

/* ---------- 新增 / 编辑 ---------- */

// 记一笔：把表单重置为默认值（类型=支出、日期=今天）后打开弹窗
function openCreate() {
  editingId.value = null
  Object.assign(form, {
    type: 2,
    categoryId: null,
    categoryName: '',
    amount: null,
    bizDate: dayjs().format('YYYY-MM-DD'),
    memberId: '',
    merchant: '',
    region: '',
    tags: [],
    paymentMethod: '',
    note: '',
  })
  dialogVisible.value = true
}

// 编辑：整行数据回填到表单；categoryName 重新由分类树拼出含父级的路径
function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    type: row.type,
    categoryId: row.categoryId,
    categoryName: findPath(treeByType[row.type], row.categoryId)?.join(' / ') || '',
    amount: row.amount,
    bizDate: row.bizDate,
    memberId: row.memberId || '',
    merchant: row.merchant || '',
    region: row.region || '',
    // 库内标签以英文逗号拼接存储，拆分时兼容中/英文逗号并丢弃空串
    tags: row.tags ? row.tags.split(/[,，]/).filter(Boolean) : [],
    paymentMethod: row.paymentMethod || '',
    note: row.note || '',
  })
  dialogVisible.value = true
}

// ===== 保存流水（新增/编辑共用，按 editingId 区分） =====
async function save() {
  // 先做整体表单校验，不通过会抛异常并中止保存
  await formRef.value.validate()
  saving.value = true
  try {
    // 组装提交参数：空串/空标签统一转 null（不覆盖原值）；tags 数组按英文逗号拼接成字符串
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

// 逻辑删除：先弹二次确认，提示语中说明“删除后可在数据库恢复”
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

/** 导出当前筛选条件下的全部流水为 CSV（含 BOM，Excel 直接打开不乱码） */
async function onExport() {
  const params = {
    type: filters.type || undefined,
    categoryId: filters.categoryId || undefined,
    memberId: filters.memberId || undefined,
    keyword: filters.keyword || undefined,
    startDate: filters.dateRange?.[0],
    endDate: filters.dateRange?.[1],
  }
  const data = await exportTransactions(params)
  if (!data.count) {
    ElMessage.warning('当前筛选条件下没有可导出的记录')
    return
  }
  // 下载实现：Blob → 临时 URL → 模拟点击 <a> 触发下载，最后释放临时 URL 防止内存泄漏
  const blob = new Blob([data.csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `管家婆收支明细_${dayjs().format('YYYYMMDD-HHmmss')}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${data.count} 条记录`)
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
      <!-- 工具条右侧：导出 CSV（忽略分页，按当前筛选导出全部） 与 记一笔 -->
      <div class="spacer"></div>
      <el-button :icon="Download" @click="onExport">导出 CSV</el-button>
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

    <!-- 分页器：改页码直接 load；改每页条数回到第 1 页（search） -->
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
          <el-radio-group v-model="form.type" @change="onTypeChange">
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
        <!-- 分类：弹层树选择，父分类点击展开、叶子点击选中；展示框显示含父级的完整路径 -->
        <el-form-item label="分类" prop="categoryId">
          <el-popover v-model:visible="catPopVisible" placement="bottom-start" :width="300" trigger="click">
            <div style="max-height: 320px; overflow: auto">
              <el-tree
                :data="treeByType[form.type]"
                node-key="value"
                :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
                :expand-on-click-node="false"
                highlight-current
                empty-text="该类型暂无分类"
                @node-click="onCategoryClick"
              />
            </div>
            <template #reference>
              <el-input
                readonly
                :model-value="form.categoryName"
                :placeholder="treeByType[form.type].length ? '点击父分类展开，选最末级分类' : '该类型暂无分类'"
                style="width: 280px; cursor: pointer"
              >
                <template #suffix><el-icon style="color: var(--el-text-color-placeholder)"><ArrowDown /></el-icon></template>
              </el-input>
            </template>
          </el-popover>
        </el-form-item>
        <el-form-item label="日期" prop="bizDate">
          <el-date-picker v-model="form.bizDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="成员">
          <el-select v-model="form.memberId" placeholder="不选=家庭整体" style="width: 200px">
            <el-option :value="''" label="👪 家庭整体" />
            <el-option v-for="m in store.memberOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="商家">
          <el-input v-model="form.merchant" placeholder="如：美团外卖 / XX超市" maxlength="100" />
        </el-form-item>
        <el-form-item label="片区">
          <el-input v-model="form.region" placeholder="如：朝阳区望京" maxlength="50" />
        </el-form-item>
        <!-- 标签：可多选；filterable + allow-create 支持搜索已有标签、回车自建新标签 -->
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
