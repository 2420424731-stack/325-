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
const store = useUserStore()

const month = ref(currentMonth())
const loading = ref(false)
const rows = ref([])
const tree = ref([])

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ categoryId: null, amount: null })

const rules = {
  amount: [{ required: true, message: '请输入预算金额', trigger: 'blur' }],
}

onMounted(async () => {
  tree.value = await categoryTree(2).then(toTreeProps)
  load()
})

async function load() {
  loading.value = true
  try {
    rows.value = await budgetExecution(month.value)
  } finally {
    loading.value = false
  }
}

function toTreeProps(nodes) {
  return (nodes || []).map((n) => ({
    value: n.id,
    label: `${n.icon || ''} ${n.name}`.trim(),
    disabled: n.status === 0,
    children: n.children?.length ? toTreeProps(n.children) : undefined,
  }))
}

function rateColor(rate) {
  if (Number(rate) > 100) return 'var(--st-critical)'
  if (Number(rate) >= 80) return 'var(--st-warning)'
  return 'var(--series-1)'
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { categoryId: null, amount: null })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.budgetId
  Object.assign(form, { categoryId: row.categoryId || null, amount: Number(row.amount) })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      categoryId: form.categoryId || null,
      budgetMonth: month.value,
      amount: form.amount,
    }
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
      <el-table-column label="操作" width="120" v-if="store.isAdmin">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="该月还没有预算，点击右上角新增" :image-size="72" />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑预算' : '新增预算'" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
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
