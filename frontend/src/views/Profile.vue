<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getFamily, updateFamily } from '../api/family'
import { useUserStore } from '../stores/user'

/**
 * 个人中心（设计文档 7.3）：账号信息 + 家庭信息（管理员可编辑家庭名称/描述）
 * 注：修改密码接口后端尚未提供（设计文档 6.3 规划中），暂未开放
 */
const store = useUserStore()

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ name: '', description: '' })

const rules = {
  name: [
    { required: true, message: '请输入家庭名称', trigger: 'blur' },
    { max: 50, message: '家庭名称最长 50 字符', trigger: 'blur' },
  ],
}

onMounted(async () => {
  await store.fetchContext()
})

function openEdit() {
  Object.assign(form, {
    name: store.family?.name || '',
    description: store.family?.description || '',
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await updateFamily({ name: form.name, description: form.description || null })
    store.family = await getFamily()
    ElMessage.success('已保存')
    dialogVisible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <el-row :gutter="12">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="card-title">账号信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ store.user?.nickname || '--' }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag :type="store.isAdmin ? 'warning' : 'info'" size="small">
                {{ store.isAdmin ? '家庭管理员（户主）' : '家庭成员' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            class="tip"
            type="info"
            :closable="false"
            title="修改密码功能待后端提供接口后开放"
            show-icon
          />
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">家庭信息</span>
              <el-button v-if="store.isAdmin" link type="primary" @click="openEdit">编辑</el-button>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="家庭名称">{{ store.family?.name }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ store.family?.description || '--' }}</el-descriptions-item>
            <el-descriptions-item label="成员数">{{ store.members.length }} 人</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" title="编辑家庭信息" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="家庭名称" prop="name">
          <el-input v-model="form.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-title {
  font-weight: 600;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tip {
  margin-top: 12px;
}
</style>
