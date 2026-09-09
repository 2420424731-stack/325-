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
// 全局用户状态：isAdmin 决定是否显示新增/编辑/删除入口
const store = useUserStore()

// 家庭成员关系候选列表（下拉框选项，与后端约定一致）
const RELATIONS = ['户主', '配偶', '子女', '父母', '其他']

// ===== 页面状态 =====
const loading = ref(false)
const rows = ref([]) // 成员列表数据

// ===== 弹窗表单状态 =====
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null) // 正在编辑的成员 id，null = 新增模式
const formRef = ref(null)
// 弹窗表单：relation 默认「其他」；status 用 1 = 启用 / 0 = 停用（与开关绑定值一致）
const form = reactive({ name: '', relation: '其他', birthday: null, sortOrder: 0, status: 1 })

// ===== 弹窗表单校验规则 =====
const rules = {
  name: [
    { required: true, message: '请输入成员姓名', trigger: 'blur' },
    { max: 50, message: '成员姓名最长 50 字符', trigger: 'blur' },
  ],
}

// ===== 初始化与数据加载 =====
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

// ===== 弹窗操作：打开新增 / 编辑 =====
// 打开新增时重置为默认值（关系「其他」、默认启用）
function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', relation: '其他', birthday: null, sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  // 编辑时回填该行数据；个别字段为空时回退到默认值
  Object.assign(form, {
    name: row.name,
    relation: row.relation || '其他',
    birthday: row.birthday || null,
    sortOrder: row.sortOrder || 0,
    status: row.status,
  })
  dialogVisible.value = true
}

// ===== 保存（新增或更新） =====
async function save() {
  await formRef.value.validate() // 先通过表单校验，校验失败会抛错中止保存
  saving.value = true
  try {
    const payload = {
      name: form.name,
      relation: form.relation,
      birthday: form.birthday || null, // 未选生日时提交 null
      sortOrder: form.sortOrder || 0,
      status: form.status,
    }
    // editingId 非空走更新接口，否则走新增接口；成功后关闭弹窗并刷新列表
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

// ===== 删除：先弹确认框，确认后再调接口 =====
// 「户主」与已有流水记录的成员由后端禁止删除（接口会报错，前端不做二次限制）
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
    <!-- 工具栏：说明文案 + 新增成员按钮（按钮仅管理员可见） -->
    <div class="page-toolbar">
      <span class="hint">成员用于按人统计收支；「户主」或有流水记录的成员不可删除</span>
      <div class="spacer"></div>
      <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openCreate">新增成员</el-button>
    </div>

    <!-- 成员列表表格：成员将作为全站「按人统计收支」的维度 -->
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="name" label="姓名" min-width="120" />
      <!-- 关系列：户主（一家之主，唯一）用黄色标签突出显示 -->
      <el-table-column label="关系" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.relation === '户主'" type="warning" size="small">户主</el-tag>
          <span v-else>{{ row.relation || '--' }}</span>
        </template>
      </el-table-column>
      <!-- 生日列：统一经 date() 工具函数格式化后显示 -->
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
      <!-- 操作列：仅管理员可见（成员管理属于家庭级管理操作） -->
      <el-table-column label="操作" width="140" v-if="store.isAdmin">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑成员弹窗（标题随模式切换） -->
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
        <!-- 关系下拉：选项来自常量 RELATIONS，可作为筛选与统计维度 -->
        <el-form-item label="关系">
          <el-select v-model="form.relation" style="width: 100%">
            <el-option v-for="r in RELATIONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <!-- 生日：可留空；选中后按 YYYY-MM-DD 格式提交 -->
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <!-- 状态开关：绑定值 1 = 启用 / 0 = 停用，与后端 status 字段保持一致 -->
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
