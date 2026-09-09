<script setup>
/**
 * 主布局骨架：左侧墨绿导航侧栏 + 右侧顶栏（标题/提醒铃铛/用户菜单）+ 中央内容区。
 * 除登录/注册外，各业务页面都作为子路由在此处的 <router-view /> 内切换渲染
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Bell, Collection, DataAnalysis, Odometer, Setting, Tickets, TrendCharts, User, Wallet } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { anomalies } from '../api/analysis'
import { listLoans } from '../api/loan'
import { currentMonth, date, money } from '../utils/format'

// 全局状态与路由引用：store 保存登录用户/当前家庭；route 提供当前路径；router 用于跳转
const store = useUserStore()
const route = useRoute()
const router = useRouter()

onMounted(() => {
  // 每次刷新后补齐家庭/成员上下文（直接刷新页面时 store 尚未加载，须重新拉取）
  if (!store.family) {
    // 拉取失败静默忽略：本框架页不依赖这些数据，缺上下文由子页面各自兜底
    store.fetchContext().catch(() => {})
  }
  loadNotify() // 初始化先拉一次铃铛，让角标数字在未打开弹层前就显示出来
})

// ===== 退出登录 =====
async function onLogout() {
  // 弹确认框：用户点"确定"才继续，点"取消"时 confirm 抛错、函数在此中止
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await store.logout() // 清理本地登录态
  router.push('/login') // 最后跳回登录页，完成登出闭环
}

/* ---------- 顶栏提醒铃铛：本月预警（含预算超支）+ 贷款还款日 ---------- */
// notify：拼装后的提醒条目；notifyLoading：加载中标记（同时充当防重入开关）
const notify = ref([])
const notifyLoading = ref(false)

/** 贷款下次还款日：每月与起贷日同号，遇短月取当月最后一天 */
function nextPayday(loan) {
  const payDay = dayjs(loan.startDate).date() // 起贷日是几号，还款日就定为每月几号
  const now = dayjs()
  // Math.min(payDay, daysInMonth())：当月没有此日期（如 31 日遇短月）时顺延为当月最后一天
  let due = now.date(Math.min(payDay, now.daysInMonth()))
  if (due.isBefore(now, 'day')) {
    // 本月应还日已过去 → 下月同号才是下一次还款日，再次做短月保护
    const next = now.add(1, 'month')
    due = next.date(Math.min(payDay, next.daysInMonth()))
  }
  return due
}

async function loadNotify() {
  if (notifyLoading.value) return // 已在请求中则忽略，防弹层反复打开造成重复请求
  notifyLoading.value = true
  try {
    const items = [] // 先在局部收集，全部处理完再一次赋值，避免列表逐条刷屏
    // 1) 本月预警（R1~R7 中 danger/warning，含 R3 预算超支）→ 跳统计分析页
    const an = (await anomalies(currentMonth())) || []
    for (const a of an) {
      if (a.level === 'info') continue // info 级属"温馨提示"，不占用铃铛提醒
      items.push({
        key: `anomaly-${a.ruleCode}-${a.dimension}`,
        kind: 'anomaly',
        level: a.level,
        icon: a.level === 'danger' ? '⛔' : '⚠️',
        title: a.title,
        desc: a.description,
      })
    }
    // 2) 贷款还款日提醒：未结清且还款日在未来 7 天内 → 跳资产管理
    const loans = (await listLoans()) || []
    for (const l of loans) {
      if (!l.remainingPrincipal || Number(l.remainingPrincipal) <= 0) continue // 已结清不再提醒
      const due = nextPayday(l)
      const days = due.diff(dayjs(), 'day')
      if (days >= 0 && days <= 7) {
        items.push({
          key: `loan-${l.id}`,
          kind: 'loan',
          level: 'warning',
          icon: '🏦',
          title: `「${l.name}」本月还款提醒`,
          desc: `${date(due.format('YYYY-MM-DD'))} 应还约 ¥${money(l.monthlyPayment)}，剩余本金 ¥${money(l.remainingPrincipal)}`,
        })
      }
    }
    notify.value = items
  } finally {
    notifyLoading.value = false // 无论成败都复位，防止异常后铃铛一直处于加载态
  }
}

// ===== 点击提醒条目 =====
// 贷款类提醒去资产管理页(/assets)；规则预警类去统计分析页(/analysis) 查看详情
function onNotifyClick(item) {
  router.push(item.kind === 'loan' ? '/assets' : '/analysis')
}
</script>

<template>
  <el-container class="layout">
    <!-- ===== 墨绿侧栏 ===== -->
    <el-aside width="220px" class="layout-aside">
      <!-- 侧栏顶部品牌区：金币图标 + 产品名 -->
      <div class="brand">
        <div class="brand-coin">
          <span class="brand-coin-rim">¥</span>
        </div>
        <div class="brand-text">
          <div class="brand-name">管家婆</div>
          <div class="brand-sub">家庭收支管理</div>
        </div>
      </div>

      <!-- 主导航菜单：router 模式下点击菜单项即按 index 路由跳转；
           :default-active="route.path" 让当前路由对应的菜单自动高亮 -->
      <el-menu router :default-active="route.path" class="layout-menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon><span>首页仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/transactions">
          <el-icon><Tickets /></el-icon><span>收支管理</span>
        </el-menu-item>
        <el-menu-item index="/categories">
          <el-icon><Collection /></el-icon><span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/members">
          <el-icon><User /></el-icon><span>成员管理</span>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <el-icon><TrendCharts /></el-icon><span>统计分析</span>
        </el-menu-item>
        <el-menu-item index="/assets">
          <el-icon><Wallet /></el-icon><span>资产管理</span>
        </el-menu-item>
        <el-menu-item index="/budgets">
          <el-icon><DataAnalysis /></el-icon><span>预算管理</span>
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><Setting /></el-icon><span>个人中心</span>
        </el-menu-item>
      </el-menu>

      <!-- 侧栏底部：当前家庭 -->
      <div v-if="store.family" class="side-family">
        <span class="side-family-icon">🏡</span>
        <div class="side-family-meta">
          <div class="side-family-name">{{ store.family.name }}</div>
          <div class="side-family-count">{{ store.members.length }} 位家庭成员</div>
        </div>
      </div>
    </el-aside>

    <el-container>
      <!-- ===== 顶栏 ===== -->
      <el-header class="layout-header">
        <!-- 左：当前页面标题（取自各路由配置的 meta.title，缺省显示空） -->
        <div class="header-title">{{ route.meta.title || '' }}</div>
        <!-- 右：提醒铃铛 + 当前家庭名 + 用户头像下拉 -->
        <div class="header-right">
          <!-- 提醒铃铛：数据在弹层每次打开(@show)时拉取，而非页面加载时拉——
               这样打开弹层时提醒内容一定是最新的 -->
          <el-popover trigger="click" :width="340" popper-class="notify-pop" @show="loadNotify">
            <template #reference>
              <!-- 铃铛按钮本体：el-badge 显示红点角标，数字为提醒条数；
                   无提醒时 :hidden 隐藏角标，条数超过 99 显示 99+ -->
              <el-badge :value="notify.length" :hidden="notify.length === 0" :max="99" class="notify-badge">
                <button class="notify-btn" title="提醒">
                  <el-icon :size="16"><Bell /></el-icon>
                </button>
              </el-badge>
            </template>
            <div class="notify-head">
              <span class="notify-title">🔔 待办提醒</span>
              <span class="notify-count">{{ notify.length ? `共 ${notify.length} 条` : '' }}</span>
            </div>
            <!-- 弹层内容：无提醒时展示空态安抚文案；加载期间列表区域由 v-loading 遮罩 -->
            <el-empty
              v-if="!notifyLoading && !notify.length"
              description="暂无待办提醒，一切正常 🎉"
              :image-size="56"
            />
            <div v-loading="notifyLoading" class="notify-list">
              <div
                v-for="n in notify"
                :key="n.key"
                class="notify-item"
                @click="onNotifyClick(n)"
              >
                <span class="notify-item-icon">{{ n.icon }}</span>
                <div class="notify-item-body">
                  <div class="notify-item-title">{{ n.title }}</div>
                  <div class="notify-item-desc">{{ n.desc }}</div>
                </div>
                <span class="notify-go">→</span>
              </div>
            </div>
          </el-popover>
          <!-- 当前家庭名徽章：仅当拉取到家庭上下文(v-if)时才显示 -->
          <span class="family-name" v-if="store.family">{{ store.family.name }}</span>
          <!-- 用户下拉：左侧头像取昵称首字，选中菜单项后经 command 命令回调 onLogout -->
          <el-dropdown @command="onLogout">
            <span class="user-chip">
              <el-avatar :size="28" class="user-avatar">{{ (store.user?.nickname || '?')[0] }}</el-avatar>
              {{ store.user?.nickname || store.user?.username }}
              <el-icon class="user-caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 中央内容区：按当前路由渲染对应子页面，与侧栏菜单的高亮联动 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
/* ===== 整体骨架 ===== */
/* 布局根占满整个视口高度，配合 el-header/el-aside/el-main 的纵向 flex 布局 */
.layout {
  height: 100%;
}

/* ===== 侧栏（墨绿渐变 + 白字菜单） ===== */
.layout-aside {
  background: linear-gradient(180deg, #17623e 0%, #0d452a 100%);
  display: flex;
  flex-direction: column;
  box-shadow: 6px 0 18px -12px rgba(11, 48, 30, 0.4);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.09);
}

.brand-coin {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 28%, #ffe9a0, #f6c453 45%, #d9a63a 82%, #c08f2a);
  box-shadow:
    inset 0 2px 4px rgba(255, 255, 255, 0.6),
    inset 0 -4px 6px rgba(150, 100, 20, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-coin-rim {
  width: 27px;
  height: 27px;
  border-radius: 50%;
  border: 1.6px dashed rgba(173, 118, 22, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  color: #8a6410;
}

.brand-name {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 3px;
  color: #ffffff;
  line-height: 1.2;
}

.brand-sub {
  font-size: 10.5px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.55);
  margin-top: 3px;
}

.layout-menu {
  flex: 1;
  border-right: none;
  padding: 12px 12px;
  overflow-y: auto;
  /* 深色菜单变量 */
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(255, 255, 255, 0.78);
  --el-menu-active-color: #ffffff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.09);
  --el-menu-item-height: 44px;
}

/* 菜单项修饰：圆角悬浮块；选中项加半透明白底与金色指示条（见 .is-active 规则） */
.layout-menu :deep(.el-menu-item) {
  position: relative;
  margin-bottom: 5px;
  border-radius: 10px;
  font-size: 14px;
  transition: background 0.18s, color 0.18s;
}

.layout-menu :deep(.el-menu-item .el-icon) {
  font-size: 17px;
}

.layout-menu :deep(.el-menu-item:hover) {
  color: #ffffff;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
  font-weight: 600;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.14),
    0 4px 12px -6px rgba(0, 0, 0, 0.35);
}

.layout-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 18px;
  border-radius: 0 4px 4px 0;
  background: linear-gradient(180deg, #f6c453, #d9a63a);
}

/* 侧栏底部家庭卡 */
.side-family {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.07);
}

.side-family-icon {
  font-size: 20px;
}

.side-family-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-family-count {
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.55);
  margin-top: 2px;
}

/* ===== 顶栏 ===== */
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #e7eee8;
  padding: 0 20px;
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  color: #173a26;
  letter-spacing: 0.5px;
}

/* 标题左侧的绿色竖条装饰 */
.header-title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 16px;
  border-radius: 3px;
  background: linear-gradient(180deg, #1b623c, #57a276);
  margin-right: 10px;
  vertical-align: -2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.family-name {
  font-size: 13px;
  color: #1b623c;
  background: #e9f5ec;
  padding: 4px 12px;
  border-radius: 12px;
  font-weight: 500;
}

/* 提醒铃铛 */
.notify-badge {
  display: inline-flex;
}

.notify-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid #ddebe1;
  background: #f2f7f3;
  color: #1b623c;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.18s, transform 0.15s;
}

.notify-btn:hover {
  background: #e2efe5;
  transform: translateY(-1px);
}

/* 顶栏用户区：头像 + 昵称组成整体，点击展开下拉（el-dropdown 的触发元素） */
.user-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  font-size: 13.5px;
  color: #26362b;
  outline: none;
  padding: 4px 8px;
  border-radius: 18px;
  transition: background 0.18s;
}

.user-chip:hover {
  background: #f1f7f2;
}

.user-avatar {
  background: linear-gradient(135deg, #1b623c, #57a276);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.user-caret {
  font-size: 12px;
  color: var(--ink-muted);
}

/* 内容区：统一留出页面内边距，子页面内容超高时在此区域内滚动 */
.layout-main {
  padding: 18px 20px;
  overflow-y: auto;
}

</style>

<style>
/* ===== 铃铛弹层全局样式 =====
   弹层内容会被 el-popover 传送到 <body> 下渲染，scoped 的属性选择器命中不到它，
   因此单独用非 scoped 样式，并借助 popper-class="notify-pop" 限定作用范围 */
.notify-pop {
  padding: 10px !important;
  border-radius: 12px;
}

.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 6px 8px;
  border-bottom: 1px solid rgba(11, 11, 11, 0.06);
  margin-bottom: 4px;
}

.notify-title {
  font-weight: 700;
  font-size: 13.5px;
  color: #1c3a29;
}

.notify-count {
  font-size: 12px;
  color: var(--ink-muted, #898781);
}

.notify-list {
  max-height: 340px;
  overflow-y: auto;
}

.notify-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 9px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}

.notify-item:hover {
  background: #eef6ef;
}

.notify-item-icon {
  font-size: 15px;
  line-height: 20px;
  flex-shrink: 0;
}

.notify-item-body {
  flex: 1;
  min-width: 0;
}

.notify-item-title {
  font-size: 13px;
  font-weight: 600;
  color: #14341f;
}

/* 提醒描述文字最多两行，超出自动以省略号截断 */
.notify-item-desc {
  font-size: 12px;
  color: #6b8f78;
  line-height: 1.5;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notify-go {
  color: #b9cfbf;
  font-size: 12px;
  margin-top: 4px;
}
</style>
