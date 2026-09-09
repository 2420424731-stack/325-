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
// 全局用户状态：仅管理员（isAdmin）可增删改分类；内置分类由后端保护
const store = useUserStore()

// ===== 页面状态 =====
const activeType = ref(2) // 当前 Tab 的分类类型：1 = 收入，2 = 支出（默认显示支出）
const loading = ref(false)
const tree = ref([]) // 树形分类数据，直接作为 el-table 树形表格的数据源

// ===== 弹窗表单状态 =====
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null) // 正在编辑的分类 id，null = 新增模式
const formRef = ref(null)
// 弹窗表单：parentId 为空表示顶级分类（后端约定顶级分类 parentId = 0）
const form = reactive({ name: '', parentId: null, icon: '', sortOrder: 0 })

// ===== 弹窗表单校验规则 =====
const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '分类名称最长 50 字符', trigger: 'blur' },
  ],
}

// 工具栏右侧的提示文案，key 与分类类型一致（1 = 收入，2 = 支出）
const RELATION_HINTS = {
  1: '收入分类（如：工资奖金、投资收益）',
  2: '支出分类（如：餐饮支出 → 外卖）',
}

// ===== 初始化与数据加载 =====
onMounted(load)

async function load() {
  loading.value = true
  try {
    // 按当前 Tab 类型拉取分类树（接口已按父子关系组装好）
    tree.value = await categoryTree(activeType.value)
  } finally {
    loading.value = false
  }
}

// 切换收入/支出 Tab 后重新加载对应类型的分类树
function onTabChange() {
  load()
}

// ===== 弹窗操作：打开新增 / 编辑 =====
// 「新增子分类」会把该行父分类带进表单；工具栏按钮则传 null = 新增顶级分类
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
    // 后端用 0 表示顶级分类，而树选择器用 null 表示「无上级」，这里做一次转换
    parentId: row.parentId === 0 ? null : row.parentId,
    icon: row.icon || '',
    sortOrder: row.sortOrder || 0,
  })
  dialogVisible.value = true
}

// ===== 保存（新增或更新） =====
async function save() {
  await formRef.value.validate() // 先通过表单校验，校验失败会抛错中止保存
  saving.value = true
  try {
    const payload = {
      type: activeType.value, // 分类类型跟随当前 Tab
      name: form.name,
      parentId: form.parentId || 0, // 空值转 0（后端约定顶级分类用 0 表示）
      icon: form.icon || null,
      sortOrder: form.sortOrder || 0,
    }
    // editingId 非空走更新接口，否则走新增接口；成功后关闭弹窗并刷新树
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

// ===== 删除：先弹确认框，确认后再调接口 =====
// 内置分类除前端禁用按钮外，后端也会拒绝删除（双重保护）
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
    <!-- 工具栏：收入/支出 Tab 切换分类口径，新增按钮仅管理员可见 -->
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

    <!-- 分类树形表格：row-key + tree-props 声明后，el-table 会按 children 字段递归展开成树 -->
    <el-table
      v-loading="loading"
      :data="tree"
      row-key="id"
      :tree-props="{ children: 'children' }"
      default-expand-all
    >
      <!-- 名称列：图标 + 名称；isSystem = 1 的系统内置分类加「内置」标签（不可删改） -->
      <el-table-column label="分类名称" min-width="240">
        <template #default="{ row }">
          <span class="cat-name">
            <span v-if="row.icon" class="cat-icon">{{ row.icon }}</span>
            {{ row.name }}
            <el-tag v-if="row.isSystem === 1" size="small" type="info" class="sys-tag">内置</el-tag>
          </span>
        </template>
      </el-table-column>
      <!-- 排序列：数值越小越靠前（影响下拉等展示顺序） -->
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <!-- 状态列：停用的分类在记账等场景不能再被选用 -->
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 操作列：仅管理员可见；内置分类（isSystem = 1）禁用删除按钮 -->
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

    <!-- 新增/编辑分类弹窗；「新增子分类」入口会预填上级分类 -->
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
        <!-- 上级分类：留空 = 顶级分类；:key="activeType" 使切换 Tab 后重建下拉树，
             避免残留上一个类型的缓存选项 -->
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
