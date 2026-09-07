<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { addMember, deleteMember, listMembers, updateMember } from '../api/member'
import { useUserStore } from '../stores/user'
import { date } from '../utils/format'

/**
 * 成员管理（设计文档 7.3）：增删改、关系维护；
 * 户主与有流水记录的成员由后端禁删
 */
const store = useUserStore()

const RELATIONS = ['户主', '配偶', '子女', '父母', '其他']

const loading = ref(false)
const rows = ref([])

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ name: '', relation: '其他', birthday: null, sortOrder: 0, status: 1 })

const rules = {
  name: [
    { required: true, message: '请输入成员姓名', trigger: 'blur' },
    { max: 50, message: '成员姓名最长 50 字符', trigger: 'blur' },
  ],
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    rows.value = await listMembers()
    // 成员列表与全局状态同步（全站下拉用）
    store.members = rows.value
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', relation: '其他', birthday: null, sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    relation: row.relation || '其他',
    birthday: row.birthday || null,
    sortOrder: row.sortOrder || 0,
    status: row.status,
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      name: form.name,
      relation: form.relation,
      birthday: form.birthday || null,
      sortOrder: form.sortOrder || 0,
      status: form.status,
    }
    if (editingId.value) {
      await updateMember(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await addMember(payload)
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除成员「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteMember(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="page-toolbar">
      <span class="hint">成员用于按人统计收支；「户主」或有流水记录的成员不可删除</span>
      <div class="spacer"></div>
      <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openCreate">新增成员</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="name" label="姓名" min-width="120" />
      <el-table-column label="关系" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.relation === '户主'" type="warning" size="small">户主</el-tag>
          <span v-else>{{ row.relation || '--' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="生日" width="120">
        <template #default="{ row }">{{ date(row.birthday) }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" v-if="store.isAdmin">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑成员' : '新增成员'"
      width="440px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="如：爸爸 / 妈妈" maxlength="50" />
        </el-form-item>
        <el-form-item label="关系">
          <el-select v-model="form.relation" style="width: 100%">
            <el-option v-for="r in RELATIONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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
</style>
