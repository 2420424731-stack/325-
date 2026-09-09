import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由（设计文档 7.2）+ 登录守卫（设计文档 7.5）：
 * 未登录一律回 /login；已登录访问 /login、/register 重定向 /dashboard
 */
const routes = [
  // 站外独立页面：登录/注册不套 Layout 外壳，且是守卫允许未登录访问的两个例外（见下方 beforeEach 特判）
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue') },
  // 后台主框架 '/'：外层套 Layout 布局（侧栏 + 顶栏），redirect 使访问根路径时默认进仪表盘
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    // children 即 Layout 的侧栏菜单对应的各业务页；路由组件均用 () => import() 懒加载（首屏只下载当前页代码）
    // meta.title 是该页标题，Layout 顶栏用它显示当前所在页面名称
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '首页仪表盘' } },
      { path: 'transactions', name: 'transactions', component: () => import('../views/Transactions.vue'), meta: { title: '收支管理' } },
      { path: 'categories', name: 'categories', component: () => import('../views/Categories.vue'), meta: { title: '分类管理' } },
      { path: 'members', name: 'members', component: () => import('../views/Members.vue'), meta: { title: '成员管理' } },
      { path: 'analysis', name: 'analysis', component: () => import('../views/Analysis.vue'), meta: { title: '统计分析' } },
      { path: 'assets', name: 'assets', component: () => import('../views/Assets.vue'), meta: { title: '资产管理' } },
      { path: 'budgets', name: 'budgets', component: () => import('../views/Budgets.vue'), meta: { title: '预算管理' } },
      { path: 'profile', name: 'profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心' } },
    ],
  },
  // 兜底路由：用户手输的地址匹配不到任何路由（404）时，一律拉回仪表盘，避免出现空白页
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

// 创建路由实例并启用 history 模式：URL 形如 /dashboard（无 #），需后端把未知路径统一转发回 index.html
const router = createRouter({
  history: createWebHistory(),
  routes,
})

// ===== 全局登录守卫（beforeEach 在每次路由跳转前执行） =====
router.beforeEach((to) => {
  // token 由 stores/user.js 登录时写入 localStorage，是判断「是否已登录」的唯一凭证
  const token = localStorage.getItem('token')
  // 未登录且目的地不是登录/注册页 → 拦回登录页，并把原地址放进 query（Login.vue 登录成功后按 redirect 跳回）
  if (!token && to.name !== 'login' && to.name !== 'register') {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (token && (to.name === 'login' || to.name === 'register')) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
