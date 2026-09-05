<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await store.login({ ...form })
    await store.fetchContext()
    router.push(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <div class="auth-brand">
        <div class="auth-logo">🏠</div>
        <h1 class="auth-title">管家婆</h1>
        <p class="auth-sub">家庭收支管理系统</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" size="large" class="auth-submit" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
      <div class="auth-footer">
        还没有账号？<router-link to="/register">注册新家庭</router-link>
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
  width: 380px;
  padding: 12px 8px;
  border-radius: 10px;
}

.auth-brand {
  text-align: center;
  margin-bottom: 20px;
}

.auth-logo {
  font-size: 42px;
}

.auth-title {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
  color: var(--ink-primary);
}

.auth-sub {
  font-size: 13px;
  color: var(--ink-muted);
  margin-top: 2px;
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
