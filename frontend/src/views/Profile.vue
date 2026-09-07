<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '../api/auth'
import { getFamily, updateFamily } from '../api/family'
import { useUserStore } from '../stores/user'

/**
 * 个人中心（设计文档 7.3）：账号信息 + 家庭信息（管理员可编辑家庭名称/描述）
 * 修改密码：校验原密码后更新（PUT /api/auth/password）
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

/* ---------- 修改密码 ---------- */

const pwdDialogVisible = ref(false)
const pwdSaving = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度须在 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function openPwd() {
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  pwdDialogVisible.value = true
}

async function savePwd() {
  await pwdFormRef.value.validate()
  pwdSaving.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码已修改，下次登录请使用新密码')
    pwdDialogVisible.value = false
  } finally {
    pwdSaving.value = false
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
          <el-button class="tip" type="primary" plain @click="openPwd">修改密码</el-button>
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

    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="440px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="savePwd">保存</el-button>
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
