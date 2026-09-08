<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { User, Lock } from '@element-plus/icons-vue'

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
  <div class="login-layout">
    <!-- 背景漂浮装饰 -->
    <span class="blob blob-1"></span>
    <span class="blob blob-2"></span>
    <span class="blob blob-3"></span>

    <!-- ===== 左侧：温馨家庭插画 + 标语（玻璃画板） ===== -->
    <aside class="brand-side">
      <div class="brand-canvas">
        <!-- 手绘风家庭插画 -->
        <svg class="brand-illustration" viewBox="0 0 640 420" xmlns="http://www.w3.org/2000/svg">
          <!-- 太阳与光芒 -->
          <circle cx="540" cy="66" r="34" fill="#FBE9B7" />
          <!-- 云朵 -->
          <ellipse cx="120" cy="70" rx="52" ry="20" fill="#ffffff" opacity="0.9" />
          <ellipse cx="145" cy="60" rx="28" ry="16" fill="#ffffff" opacity="0.9" />
          <ellipse cx="430" cy="120" rx="42" ry="16" fill="#ffffff" opacity="0.75" />
          <!-- 远丘 -->
          <path d="M0 260 Q 130 190 260 250 T 640 240 L 640 320 L 0 320 Z" fill="#D6ECDD" />
          <!-- 近丘 -->
          <path d="M0 300 Q 160 235 340 295 T 640 285 L 640 420 L 0 420 Z" fill="#BCE0CA" />
          <!-- 大树 -->
          <rect x="92" y="250" width="14" height="60" rx="6" fill="#A98562" />
          <circle cx="99" cy="228" r="42" fill="#8CCDA4" />
          <circle cx="70" cy="244" r="26" fill="#9ED6B2" />
          <circle cx="128" cy="244" r="26" fill="#9ED6B2" />
          <!-- 房子 -->
          <rect x="290" y="196" width="150" height="124" rx="10" fill="#FFF6E6" />
          <!-- 瓦顶 -->
          <path d="M 268 208 L 365 128 L 462 208 Z" fill="#EEA37C" />
          <path d="M 268 208 L 462 208 L 462 220 L 268 220 Z" fill="#E28F64" />
          <!-- 房梁圆点 -->
          <circle cx="365" cy="146" r="7" fill="#FFF6E6" opacity="0.9" />
          <!-- 发光的圆窗 -->
          <circle cx="365" cy="238" r="26" fill="#FFE9A8" />
          <circle cx="365" cy="238" r="26" fill="none" stroke="#C98A4B" stroke-width="5" />
          <line x1="339" y1="238" x2="391" y2="238" stroke="#C98A4B" stroke-width="5" />
          <line x1="365" y1="212" x2="365" y2="264" stroke="#C98A4B" stroke-width="5" />
          <!-- 门 -->
          <rect x="322" y="272" width="38" height="48" rx="19" fill="#E9B78A" />
          <!-- 屋顶爱心 -->
          <path d="M 500 96 q -10 -18 -24 -8 q -9 9 0 20 l 24 22 l 24 -22 q 9 -11 0 -20 q -14 -10 -24 8 Z"
                fill="#F48B8B" transform="translate(-18 -6) scale(0.9)" />
          <!-- 草地上的金币 -->
          <g>
            <circle cx="470" cy="330" r="17" fill="#F0C24C" />
            <circle cx="470" cy="330" r="13" fill="#F7D97B" />
            <text x="470" y="335" text-anchor="middle" font-size="15" font-weight="700" fill="#B8860B" font-family="sans-serif">¥</text>
          </g>
          <g>
            <circle cx="556" cy="352" r="13" fill="#F0C24C" />
            <circle cx="556" cy="352" r="10" fill="#F7D97B" />
            <text x="556" y="356" text-anchor="middle" font-size="12" font-weight="700" fill="#B8860B" font-family="sans-serif">¥</text>
          </g>
          <g>
            <circle cx="216" cy="336" r="15" fill="#F0C24C" />
            <circle cx="216" cy="336" r="11" fill="#F7D97B" />
            <text x="216" y="340" text-anchor="middle" font-size="13" font-weight="700" fill="#B8860B" font-family="sans-serif">¥</text>
          </g>
          <!-- 记账本 -->
          <rect x="120" y="300" width="52" height="66" rx="8" fill="#FDFDF8" stroke="#D8E4DC" stroke-width="3" transform="rotate(-6 146 333)" />
          <line x1="134" y1="316" x2="158" y2="314" stroke="#BFD8C8" stroke-width="4" stroke-linecap="round" transform="rotate(-6 146 333)" />
          <line x1="134" y1="330" x2="158" y2="328" stroke="#BFD8C8" stroke-width="4" stroke-linecap="round" transform="rotate(-6 146 333)" />
          <line x1="134" y1="344" x2="152" y2="342" stroke="#BFD8C8" stroke-width="4" stroke-linecap="round" transform="rotate(-6 146 333)" />
          <!-- 小草与小花 -->
          <path d="M 30 368 q 8 -22 16 0" stroke="#7FC49A" stroke-width="4" fill="none" stroke-linecap="round" />
          <path d="M 560 382 q 8 -20 15 0" stroke="#7FC49A" stroke-width="4" fill="none" stroke-linecap="round" />
          <circle cx="252" cy="352" r="6" fill="#F48B8B" />
          <circle cx="252" cy="352" r="2.4" fill="#FFE9A8" />
          <circle cx="506" cy="376" r="6" fill="#F7C9C9" />
          <circle cx="506" cy="376" r="2.4" fill="#FFE9A8" />
        </svg>

        <!-- 标语 -->
        <h2 class="brand-slogan">记好每一笔账，<br />守住一家人的小日子</h2>
        <p class="brand-sub">收支记录 · 预算管理 · 资产盘点 · 智能分析<br />让家庭财务清清楚楚、心中有数</p>
      </div>
    </aside>

    <!-- ===== 右侧：登录卡片 ===== -->
    <section class="form-side">
      <div class="login-card">
        <!-- 金币 logo + 标题 -->
        <div class="card-brand">
          <div class="coin-logo">
            <span class="coin-rim">¥</span>
          </div>
          <h1 class="card-title">管家婆</h1>
          <p class="card-sub">家庭收支管理系统</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              clearable
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="Lock"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>

        <div class="card-footer">
          还没有账号？<router-link to="/register">注册新家庭</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ===== 整页统一绿色底色 ===== */
.login-layout {
  position: relative;
  height: 100%;
  display: flex;
  overflow: hidden;
  background:
    radial-gradient(1100px 660px at 88% -8%, rgba(255, 255, 255, 0.4), transparent 60%),
    radial-gradient(780px 600px at -10% 108%, rgba(255, 255, 255, 0.3), transparent 55%),
    linear-gradient(152deg, #d9f0e0 0%, #a7d8b9 45%, #6caa87 100%);
}

/* 背景漂浮气泡（白色光晕，跨左右淡出，避免割裂） */
.blob {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle at 34% 30%, rgba(255, 255, 255, 0.55), rgba(255, 255, 255, 0.08));
  pointer-events: none;
}
.blob-1 { width: 420px; height: 420px; top: -130px; left: 22%; }
.blob-2 { width: 300px; height: 300px; right: -80px; bottom: -60px; }
.blob-3 { width: 140px; height: 140px; left: 6%; bottom: 14%; opacity: 0.8; }

/* ===== 左栏：玻璃画板 ===== */
.brand-side {
  position: relative;
  z-index: 1;
  flex: 1.25;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.brand-canvas {
  max-width: 600px;
  width: 88%;
  padding: 46px 48px 40px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 24px 50px -22px rgba(23, 82, 51, 0.4);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-illustration {
  width: 100%;
  height: auto;
  filter: drop-shadow(0 16px 26px rgba(46, 110, 71, 0.18));
}

.brand-slogan {
  margin: 22px 0 0;
  font-size: 30px;
  line-height: 1.55;
  text-align: center;
  color: #123c26;
  letter-spacing: 2px;
  font-weight: 800;
}

.brand-sub {
  margin: 12px 0 0;
  font-size: 13.5px;
  line-height: 2;
  text-align: center;
  color: #3f6a50;
  letter-spacing: 1px;
}

/* ===== 右栏：登录卡 ===== */
.form-side {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.login-card {
  width: min(420px, 100%);
  padding: 46px 40px 38px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 32px 64px -26px rgba(15, 62, 37, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-sizing: border-box;
}

.card-brand {
  text-align: center;
  margin-bottom: 30px;
}

/* 金币 logo */
.coin-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
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
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 2px dashed rgba(173, 118, 22, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  color: #8a6410;
}

.card-title {
  font-size: 27px;
  font-weight: 800;
  letter-spacing: 6px;
  color: #123c26;
  margin: 0;
  text-indent: 6px;
}

.card-sub {
  font-size: 13px;
  letter-spacing: 2px;
  color: #5c8068;
  margin: 8px 0 0;
}

/* 表单 */
.login-card :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-card :deep(.el-input__wrapper) {
  border-radius: 12px;
  background: #fafdfb;
  box-shadow: 0 0 0 1.5px #d7e7da inset;
  padding: 2px 14px;
  transition: box-shadow 0.2s;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #6fae82 inset;
  background: #ffffff;
}

.login-card :deep(.el-input__prefix) {
  color: #7da58a;
}

/* 墨绿登录按钮 */
.login-btn {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border-radius: 12px;
  background: linear-gradient(180deg, #1b623c, #123f28);
  border: none;
  letter-spacing: 8px;
  font-weight: 600;
  box-shadow: 0 10px 18px -8px rgba(18, 70, 43, 0.55);
  transition: transform 0.15s, box-shadow 0.15s, filter 0.15s;
}

.login-btn:hover,
.login-btn:focus {
  background: linear-gradient(180deg, #206e45, #154b2f) !important;
  filter: brightness(1.06);
  box-shadow: 0 12px 22px -8px rgba(18, 70, 43, 0.6);
}

.login-btn:active {
  transform: translateY(1px);
}

.card-footer {
  margin-top: 20px;
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

/* ===== 响应式 ===== */
@media (max-width: 980px) {
  .brand-side { display: none; }
  .form-side { min-width: 0; padding: 24px; }
}
</style>
