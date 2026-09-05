<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { addCategory, categoryTree, deleteCategory, updateCategory } from '../api/category'
import { useUserStore } from '../stores/user'

/**
 * 分类管理（设计文档 7.3）：树形表格，收入/支出 Tab 切换；
 * 内置分类不可删除（后端同样校验），管理员可增删改
 */
const store = useUserStore()

const activeType = ref(2)
const loading = ref(false)
const tree = ref([])

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ name: '', parentId: null, icon: '', sortOrder: 0 })

const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '分类名称最长 50 字符', trigger: 'blur' },
  ],
}

const RELATION_HINTS = {
  1: '收入分类（如：工资奖金、投资收益）',
  2: '支出分类（如：餐饮支出 → 外卖）',
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    tree.value = await categoryTree(activeType.value)
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  load()
}

function openCreate(parent) {
  editingId.value = null
  Object.assign(form, {
    name: '',
    parentId: parent ? parent.id : null,
    icon: '',
    sortOrder: 0,
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    parentId: row.parentId === 0 ? null : row.parentId,
    icon: row.icon || '',
    sortOrder: row.sortOrder || 0,
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      type: activeType.value,
      name: form.name,
      parentId: form.parentId || 0,
      icon: form.icon || null,
      sortOrder: form.sortOrder || 0,
    }
    if (editingId.value) {
      await updateCategory(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await addCategory(payload)
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="page-toolbar">
      <el-radio-group v-model="activeType" @change="onTabChange">
        <el-radio-button :value="1">收入分类</el-radio-button>
        <el-radio-button :value="2">支出分类</el-radio-button>
      </el-radio-group>
      <span class="hint">{{ RELATION_HINTS[activeType] }}</span>
      <div class="spacer"></div>
      <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openCreate(null)">
        新增顶级分类
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="tree"
      row-key="id"
      :tree-props="{ children: 'children' }"
      default-expand-all
    >
      <el-table-column label="分类名称" min-width="240">
        <template #default="{ row }">
          <span class="cat-name">
            <span v-if="row.icon" class="cat-icon">{{ row.icon }}</span>
            {{ row.name }}
            <el-tag v-if="row.isSystem === 1" size="small" type="info" class="sys-tag">内置</el-tag>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" v-if="store.isAdmin">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openCreate(row)">新增子分类</el-button>
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" :disabled="row.isSystem === 1" @click="onDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑分类' : '新增分类'"
      width="440px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：外卖" maxlength="50" />
        </el-form-item>
        <el-form-item label="上级分类">
          <el-tree-select
            v-model="form.parentId"
            :data="tree"
            :key="activeType"
            placeholder="空 = 顶级分类"
            clearable
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="emoji 或图标名，如 🍚" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
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

.cat-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.cat-icon {
  font-size: 15px;
}

.sys-tag {
  margin-left: 4px;
}
</style>
