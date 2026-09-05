import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由（设计文档 7.2）+ 登录守卫（设计文档 7.5）：
 * 未登录一律回 /login；已登录访问 /login、/register 重定向 /dashboard
 * 注：资产/预算为拓展功能，待后端接口完成后补充路由
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue') },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
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
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!token && to.name !== 'login' && to.name !== 'register') {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (token && (to.name === 'login' || to.name === 'register')) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
