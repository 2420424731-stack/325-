<script setup>
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Collection, DataAnalysis, Odometer, Setting, Tickets, TrendCharts, User, Wallet } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const route = useRoute()
const router = useRouter()

onMounted(() => {
  // 每次刷新后补齐家庭/成员上下文
  if (!store.family) {
    store.fetchContext().catch(() => {})
  }
})

async function onLogout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await store.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px" class="layout-aside">
      <div class="brand">
        <span class="brand-logo">🏠</span>
        <span class="brand-name">管家婆</span>
      </div>
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
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-title">{{ route.meta.title || '' }}</div>
        <div class="header-right">
          <span class="family-name" v-if="store.family">{{ store.family.name }}</span>
          <el-dropdown @command="onLogout">
            <span class="user-chip">
              <el-avatar :size="26" class="user-avatar">{{ (store.user?.nickname || '?')[0] }}</el-avatar>
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
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background: #ffffff;
  border-right: 1px solid rgba(11, 11, 11, 0.08);
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(11, 11, 11, 0.06);
}

.brand-logo {
  font-size: 22px;
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--ink-primary);
}

.layout-menu {
  flex: 1;
  border-right: none;
  padding-top: 6px;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid rgba(11, 11, 11, 0.08);
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ink-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.family-name {
  font-size: 13px;
  color: var(--ink-secondary);
  background: rgba(137, 135, 129, 0.1);
  padding: 4px 10px;
  border-radius: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--ink-primary);
  outline: none;
}

.user-avatar {
  background: var(--series-1);
  color: #fff;
  font-size: 13px;
}

.user-caret {
  font-size: 12px;
  color: var(--ink-muted);
}

.layout-main {
  padding: 16px;
  overflow-y: auto;
}
</style>
