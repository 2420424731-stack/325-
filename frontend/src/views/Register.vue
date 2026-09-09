<script setup>
/**
 * 注册页：一次填完"账号 + 家庭"两类信息。
 * 注册接口会同时创建 账号 / 家庭 / 户主成员 / 内置收支分类，成功后即自动登录进首页
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { User, Lock, HomeFilled, Avatar } from '@element-plus/icons-vue'

const store = useUserStore()
const router = useRouter()

// ===== 表单状态与校验规则 =====
const formRef = ref(null) // 表单组件引用，用它触发整体校验 validate()
const loading = ref(false) // 提交中标记：按钮转圈防重复提交
// 注册表单数据：confirm 是"确认密码"，仅用于二次校验、不会提交给后端；nickname 选填
const form = reactive({
  username: '',
  password: '',
  confirm: '',
  nickname: '',
  familyName: '',
})

// 校验规则：用户名/密码必填且有长度限制；confirm 用自定义 validator 比对两次密码；
// 家庭名必填，因为注册时会以它为名创建家庭账本
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
      // 自定义校验：两次输入的密码不一致时报错（cb 传入 Error 即校验失败）
      validator: (_, v, cb) => (v === form.password ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur',
    },
  ],
  familyName: [{ required: true, message: '请输入家庭名称（如：张三家）', trigger: 'blur' }],
}

// ===== 提交注册（成功后自动登录并进入首页） =====
async function submit() {
  await formRef.value.validate() // 校验不通过抛异常中断，不会发出请求
  loading.value = true
  try {
    // 注册即创建账号+家庭+户主成员+内置分类，成功后自动登录
    await store.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || form.username, // 昵称留空时回退为用户名
      familyName: form.familyName,
    })
    await store.fetchContext() // 拉取新创建的家庭上下文
    router.push('/dashboard') // 已自动登录，无需再走登录页，直接进首页
  } finally {
    loading.value = false // 无论成败都恢复按钮，失败时用户可原地修改重试
  }
}
</script>

<template>
  <div class="register-layout">
    <!-- 背景漂浮装饰 -->
    <span class="blob blob-1"></span>
    <span class="blob blob-2"></span>

    <!-- 居中白色圆角卡片：账号与家庭信息一屏填完 -->
    <div class="register-card">
      <!-- 金币 logo + 标题 -->
      <div class="card-brand">
        <div class="coin-logo">
          <span class="coin-rim">¥</span>
        </div>
        <h1 class="card-title">注册新家庭</h1>
        <p class="card-sub">注册即创建家庭账本 · 自动内置常用收支分类</p>
      </div>

      <!-- 注册表单：回车或点按钮均可提交；用户名/密码/家庭名等校验规则见脚本 rules -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-20 位）" clearable :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（6-32 位）" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="确认密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item prop="familyName">
          <el-input v-model="form.familyName" placeholder="家庭名称（如：张三家）" clearable :prefix-icon="HomeFilled" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="你的昵称（选填，默认同用户名）" clearable :prefix-icon="Avatar" />
        </el-form-item>
        <!-- 提交按钮：:loading 期间自动禁用，防止重复点击注册出多个家庭 -->
        <el-button type="primary" size="large" class="register-btn" :loading="loading" @click="submit">
          注册并创建家庭
        </el-button>
      </el-form>

      <!-- 底部：已有账号则点击文字链直接回登录页 -->
      <div class="card-footer">
        已有账号？<router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 整页统一绿色底色（与登录页一致） ===== */
.register-layout {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 24px;
  background:
    radial-gradient(1100px 660px at 88% -8%, rgba(255, 255, 255, 0.4), transparent 60%),
    radial-gradient(780px 600px at -10% 108%, rgba(255, 255, 255, 0.3), transparent 55%),
    linear-gradient(152deg, #d9f0e0 0%, #a7d8b9 45%, #6caa87 100%);
}

/* 背景漂浮气泡 */
.blob {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle at 34% 30%, rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0.08));
  pointer-events: none;
}
.blob-1 { width: 380px; height: 380px; top: -120px; left: 18%; }
.blob-2 { width: 300px; height: 300px; right: -90px; bottom: -80px; }

/* 中央白色圆角卡片：z-index 抬到背景气泡之上，保证内容可点击 */
.register-card {
  position: relative;
  z-index: 1;
  width: min(460px, 100%);
  padding: 40px 42px 34px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 32px 64px -26px rgba(15, 62, 37, 0.45);
  box-sizing: border-box;
}

.card-brand {
  text-align: center;
  margin-bottom: 26px;
}

/* 金币 logo（小号） */
.coin-logo {
  width: 50px;
  height: 50px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 28%, #ffe9a0, #f6c453 45%, #d9a63a 82%, #c08f2a);
  box-shadow:
    inset 0 3px 6px rgba(255, 255, 255, 0.65),
    inset 0 -5px 8px rgba(150, 100, 20, 0.35),
    0 8px 16px -6px rgba(200, 150, 40, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}

.coin-rim {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px dashed rgba(173, 118, 22, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  font-weight: 800;
  color: #8a6410;
}

.card-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 4px;
  color: #123c26;
  margin: 0;
  text-indent: 4px;
}

.card-sub {
  font-size: 12.5px;
  letter-spacing: 1px;
  color: #5c8068;
  margin: 8px 0 0;
}

/* 表单 */
.register-card :deep(.el-form-item) {
  margin-bottom: 18px;
}

.register-card :deep(.el-input__wrapper) {
  border-radius: 12px;
  background: #fafdfb;
  box-shadow: 0 0 0 1.5px #d7e7da inset;
  padding: 2px 14px;
  transition: box-shadow 0.2s;
}

.register-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #6fae82 inset;
  background: #ffffff;
}

.register-card :deep(.el-input__prefix) {
  color: #7da58a;
}

/* 墨绿注册按钮 */
.register-btn {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  border-radius: 12px;
  background: linear-gradient(180deg, #1b623c, #123f28);
  border: none;
  letter-spacing: 3px;
  font-weight: 600;
  box-shadow: 0 10px 18px -8px rgba(18, 70, 43, 0.55);
  transition: transform 0.15s, box-shadow 0.15s, filter 0.15s;
}

.register-btn:hover,
.register-btn:focus {
  background: linear-gradient(180deg, #206e45, #154b2f) !important;
  filter: brightness(1.06);
  box-shadow: 0 12px 22px -8px rgba(18, 70, 43, 0.6);
}

.register-btn:active {
  transform: translateY(1px);
}

.card-footer {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: #5c8068;
}

.card-footer a {
  color: #1b623c;
  font-weight: 600;
  text-decoration: none;
}

.card-footer a:hover {
  text-decoration: underline;
}
</style>
