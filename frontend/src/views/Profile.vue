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
// 全局用户状态：保存当前登录用户与其所在家庭的信息（user / family / members / isAdmin），本页据此展示
const store = useUserStore()

// ===== 编辑家庭信息 =====
const dialogVisible = ref(false) // 编辑家庭信息弹窗开关
const saving = ref(false)
const formRef = ref(null) // 表单组件引用，用于触发校验
const form = reactive({ name: '', description: '' }) // 弹窗表单：家庭名称 + 描述

// ===== 弹窗表单校验规则 =====
const rules = {
  name: [
    { required: true, message: '请输入家庭名称', trigger: 'blur' },
    { max: 50, message: '家庭名称最长 50 字符', trigger: 'blur' },
  ],
}

// 进入页面先刷新一次用户/家庭上下文，保证卡片展示的是最新数据
onMounted(async () => {
  await store.fetchContext()
})

// 打开弹窗前，把 store 中当前家庭信息回填进表单（描述可能为空）
function openEdit() {
  Object.assign(form, {
    name: store.family?.name || '',
    description: store.family?.description || '',
  })
  dialogVisible.value = true
}

// ===== 保存家庭信息 =====
async function save() {
  await formRef.value.validate() // 先通过表单校验，校验失败会抛错中止保存
  saving.value = true
  try {
    // 描述留空时提交 null；保存成功后重新拉取家庭信息写回 store，供全站共享
    await updateFamily({ name: form.name, description: form.description || null })
    store.family = await getFamily()
    ElMessage.success('已保存')
    dialogVisible.value = false
  } finally {
    saving.value = false
  }
}

/* ---------- 修改密码 ---------- */

// ===== 修改密码：弹窗表单状态 =====
const pwdDialogVisible = ref(false)
const pwdSaving = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

// 密码校验规则：原密码必填、新密码 6-32 位、确认密码需与输入的新密码一致
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度须在 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      // 自定义校验器：比对两次输入的新密码，不一致则校验不通过
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function openPwd() {
  // 每次打开都先清空上一次的输入
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  pwdDialogVisible.value = true
}

// ===== 提交修改密码 =====
// 接口会先校验原密码是否正确，错误由后端返回提示，前端只负责提交
async function savePwd() {
  await pwdFormRef.value.validate()
  pwdSaving.value = true
  try {
    // 只提交原密码与新密码；确认密码仅用于前端一致性校验，不上传
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
    <!-- 两栏栅格布局：左列 = 账号信息卡片，右列 = 家庭信息卡片 -->
    <el-row :gutter="12">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="card-title">账号信息</span></template>
          <!-- 账号信息为只读展示，数据来自登录后的全局状态 store.user -->
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ store.user?.nickname || '--' }}</el-descriptions-item>
            <!-- 角色即权限来源：管理员（户主）才能执行管理类操作 -->
            <el-descriptions-item label="角色">
              <el-tag :type="store.isAdmin ? 'warning' : 'info'" size="small">
                {{ store.isAdmin ? '家庭管理员（户主）' : '家庭成员' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <!-- 修改密码：所有家庭成员均可修改自己的登录密码 -->
          <el-button class="tip" type="primary" plain @click="openPwd">修改密码</el-button>
        </el-card>
      </el-col>
      <!-- 家庭信息卡片：显示家庭名称/描述/成员数；头部「编辑」按钮仅管理员可见 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">家庭信息</span>
              <el-button v-if="store.isAdmin" link type="primary" @click="openEdit">编辑</el-button>
            </div>
          </template>
          <!-- 数据来自全局状态 store（family / members），其他页面修改后会同步刷新 -->
          <el-descriptions :column="1" border>
            <el-descriptions-item label="家庭名称">{{ store.family?.name }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ store.family?.description || '--' }}</el-descriptions-item>
            <el-descriptions-item label="成员数">{{ store.members.length }} 人</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑家庭信息弹窗：名称 + 描述（入口仅管理员可见） -->
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

    <!-- 修改密码弹窗：原密码 + 新密码 + 确认新密码（确认项仅做前端一致性校验） -->
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
