<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { budgetExecution, createBudget, deleteBudget, updateBudget } from '../api/budget'
import { categoryTree } from '../api/category'
import { useUserStore } from '../stores/user'
import { currentMonth, money } from '../utils/format'

/**
 * 预算管理（设计文档 F11 / 7.3）：
 * 按月份查看分类预算（分类口径含子孙分类）与执行率，超额标红；
 * categoryId 空 = 家庭总预算
 */
// 全局用户状态：isAdmin 决定是否显示新增/编辑/删除入口
const store = useUserStore()

// ===== 页面状态 =====
const month = ref(currentMonth()) // 当前查看/设置预算的月份，默认本月
const loading = ref(false)
const rows = ref([]) // 预算执行率列表：每行 = 一个分类预算（categoryId 为空则是家庭总预算）
const tree = ref([]) // 支出分类树（转成 el-tree-select 结构后供弹窗选择）

// ===== 弹窗表单状态 =====
const dialogVisible = ref(false)
const saving = ref(false) // 保存中的 loading 标记
const editingId = ref(null) // 正在编辑的预算 id，null = 新增模式
const formRef = ref(null) // 表单组件引用，用于触发校验
const form = reactive({ categoryId: null, amount: null }) // categoryId 留空 = 家庭总预算

// ===== 弹窗表单校验规则 =====
const rules = {
  amount: [{ required: true, message: '请输入预算金额', trigger: 'blur' }],
}

// ===== 初始化 =====
// 先拉支出分类树（2 = 支出类型）并转成树选择器格式，再加载当月预算数据
onMounted(async () => {
  tree.value = await categoryTree(2).then(toTreeProps)
  load()
})

// ===== 月份切换与数据加载 =====
// 月份选择器 change 也会触发本函数：按当前选中月份拉取各分类预算执行率
async function load() {
  loading.value = true
  try {
    rows.value = await budgetExecution(month.value)
  } finally {
    loading.value = false
  }
}

// 递归把后端分类树转成 el-tree-select 所需的 { value, label, disabled, children } 结构；
// status = 0（停用）的分类不允许再被选为预算对象，置为禁用
function toTreeProps(nodes) {
  return (nodes || []).map((n) => ({
    value: n.id,
    label: `${n.icon || ''} ${n.name}`.trim(),
    disabled: n.status === 0,
    children: n.children?.length ? toTreeProps(n.children) : undefined,
  }))
}

// ===== 执行率进度条颜色 =====
// >100% 已超支标红，80%~100% 接近上限标黄，其余为正常主色
function rateColor(rate) {
  if (Number(rate) > 100) return 'var(--st-critical)'
  if (Number(rate) >= 80) return 'var(--st-warning)'
  return 'var(--series-1)'
}

// ===== 弹窗操作：打开新增 / 编辑 =====
function openCreate() {
  editingId.value = null // 置空 id 表示进入新增模式
  // 重置表单：分类留空即代表创建「家庭总预算」
  Object.assign(form, { categoryId: null, amount: null })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.budgetId
  // 回填该行预算对象与金额；amount 先转数字，避免后端返回字符串影响计算
  Object.assign(form, { categoryId: row.categoryId || null, amount: Number(row.amount) })
  dialogVisible.value = true
}

// ===== 保存：新增或更新 =====
async function save() {
  await formRef.value.validate() // 先通过表单校验，校验失败会抛错中止保存
  saving.value = true
  try {
    const payload = {
      categoryId: form.categoryId || null, // 空值统一转 null = 家庭总预算
      budgetMonth: month.value, // 预算挂在工具栏当前选中的月份上（弹窗内只读）
      amount: form.amount,
    }
    // editingId 非空走更新接口，否则走新增接口
    if (editingId.value) {
      await updateBudget(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createBudget(payload)
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ===== 删除预算：先弹确认框，确认后再调接口，成功则刷新列表 =====
async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.categoryName}」${month.value} 的预算吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteBudget(row.budgetId)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <el-card shadow="never">
    <!-- 工具栏：月份选择器 + 新增预算按钮（按钮仅管理员可见） -->
    <div class="page-toolbar">
      <el-date-picker
        v-model="month"
        type="month"
        value-format="YYYY-MM"
        placeholder="选择月份"
        :clearable="false"
        style="width: 140px"
        @change="load"
      />
      <span class="hint">分类预算按「含子孙分类」口径统计实际支出；超额自动标红，并触发分析页预算预警</span>
      <div class="spacer"></div>
      <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openCreate">新增预算</el-button>
    </div>

    <!-- 预算执行率表格：展示预算对象、预算金额、实际支出与执行进度 -->
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="预算对象" min-width="160">
        <template #default="{ row }">
          <span class="budget-name">
            <el-tag v-if="!row.categoryId" type="warning" size="small">家庭总预算</el-tag>
            <template v-else>{{ row.categoryName }}</template>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="预算金额" width="140" align="right">
        <template #default="{ row }"><span class="num-cell">¥{{ money(row.amount) }}</span></template>
      </el-table-column>
      <el-table-column label="实际支出" width="140" align="right">
        <template #default="{ row }"><span class="num-cell">¥{{ money(row.actual) }}</span></template>
      </el-table-column>
      <el-table-column label="执行率" min-width="220">
        <template #default="{ row }">
          <div class="rate-cell">
            <!-- 进度条数值封顶 150%，避免超额太多时把进度条撑出容器 -->
            <el-progress
              :percentage="Math.min(Number(row.rate), 150)"
              :color="rateColor(row.rate)"
              :stroke-width="10"
              :show-text="false"
              class="rate-bar"
            />
            <span class="rate-text" :class="{ overrun: row.overrun }">{{ Number(row.rate).toFixed(1) }}%</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.overrun" type="danger" size="small">超支</el-tag>
          <el-tag v-else-if="Number(row.rate) >= 80" type="warning" size="small">接近上限</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <!-- 操作列：仅管理员可见（非管理员看到的是只读执行率报表） -->
      <el-table-column label="操作" width="120" v-if="store.isAdmin">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 当月还没有任何预算时的空状态提示 -->
    <el-empty v-if="!loading && !rows.length" description="该月还没有预算，点击右上角新增" :image-size="72" />

    <!-- 新增/编辑预算弹窗（标题随编辑/新增模式切换） -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑预算' : '新增预算'" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <!-- 预算对象：从支出分类树中选择，清空不选 = 家庭总预算；
             check-strictly 允许选中任意层级的父分类 -->
        <el-form-item label="预算对象">
          <el-tree-select
            v-model="form.categoryId"
            :data="tree"
            placeholder="空 = 家庭总预算"
            clearable
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
          <div class="desc-note">选父分类时，实际支出按「含子孙分类」汇总</div>
        </el-form-item>
        <!-- 月份只读展示：新预算固定挂到工具栏当前选中的月份，不能在弹窗里改 -->
        <el-form-item label="月份">
          <span>{{ month }}</span>
        </el-form-item>
        <el-form-item label="预算金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" controls-position="right" style="width: 100%" />
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
.hint {
  font-size: 12px;
  color: var(--ink-muted);
}

.budget-name {
  display: inline-flex;
  align-items: center;
}

.rate-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rate-bar {
  flex: 1;
}

.rate-text {
  font-size: 13px;
  color: var(--ink-secondary);
  min-width: 52px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.rate-text.overrun {
  color: var(--st-critical);
  font-weight: 700;
}

.desc-note {
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.5;
}
</style>
