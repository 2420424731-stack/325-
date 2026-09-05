<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirm: '',
  nickname: '',
  familyName: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度须在 3-20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须在 6-32 位', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_, v, cb) => (v === form.password ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur',
    },
  ],
  familyName: [{ required: true, message: '请输入家庭名称（如：张三家）', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    // 注册即创建账号+家庭+户主成员+内置分类，成功后自动登录
    await store.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || form.username,
      familyName: form.familyName,
    })
    await store.fetchContext()
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <div class="auth-brand">
        <h1 class="auth-title">注册新家庭</h1>
        <p class="auth-sub">注册即创建家庭账本，内置常用收支分类</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-20 位）" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（6-32 位）" show-password />
        </el-form-item>
        <el-form-item prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="确认密码" show-password />
        </el-form-item>
        <el-form-item prop="familyName">
          <el-input v-model="form.familyName" placeholder="家庭名称（如：张三家）" clearable />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="你的昵称（选填，默认同用户名）" clearable />
        </el-form-item>
        <el-button type="primary" size="large" class="auth-submit" :loading="loading" @click="submit">
          注册并创建家庭
        </el-button>
      </el-form>
      <div class="auth-footer">
        已有账号？<router-link to="/login">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #eef4fc 0%, #f5f6f8 60%);
}

.auth-card {
  width: 400px;
  padding: 12px 8px;
  border-radius: 10px;
}

.auth-brand {
  text-align: center;
  margin-bottom: 18px;
}

.auth-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ink-primary);
}

.auth-sub {
  font-size: 13px;
  color: var(--ink-muted);
  margin-top: 4px;
}

.auth-submit {
  width: 100%;
}

.auth-footer {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: var(--ink-secondary);
}

.auth-footer a {
  color: var(--series-1);
  text-decoration: none;
}
</style>
