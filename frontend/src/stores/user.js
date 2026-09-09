import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

/**
 * 用户全局状态（设计文档 7.5）：
 * token 持久化到 localStorage，家庭/成员供全站下拉使用
 */
export const useUserStore = defineStore('user', {
  // ===== 响应式数据（state） =====
  // token/user 直接从 localStorage 读取作为初值：浏览器刷新后内存态清空，
  // 这样初始化时就能“还原”上一次的登录身份，实现刷新不掉线
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    family: null, // 当前家庭对象：进入系统后由 fetchContext() 拉取
    members: [], // 家庭成员列表：供收支表单、统计筛选等处的成员下拉使用
  }),
  // ===== 派生数据（getters）：由 state 计算而来，多处复用免去重复推导 =====
  getters: {
    /** 是否户主/管理员：决定成员管理、家庭设置等操作入口是否可见 */
    isAdmin: (s) => s.user?.role === 'ADMIN',
    /** 成员 id → 姓名 映射，供列表展示 */
    memberMap: (s) => Object.fromEntries((s.members || []).map((m) => [m.id, m.name])),
    memberOptions: (s) =>
      (s.members || []).map((m) => ({ value: m.id, label: m.name })),
  },
  // ===== 动作（actions）：封装登录/登出等会修改状态并调用后端的操作 =====
  actions: {
    async login(payload) {
      // 调用后端登录接口，成功后把返回的 token+user 交给 applyLogin 统一落库
      const data = await authApi.login(payload)
      this.applyLogin(data)
    },
    async register(payload) {
      // 注册接口设计为注册成功即自动登录（后端直接返回 token），因此同样走 applyLogin
      const data = await authApi.register(payload)
      this.applyLogin(data)
    },
    // 登录/注册共用收尾：更新内存态，并同步持久化到 localStorage（user 为对象须 JSON.stringify，
    // 保证刷新页面后 state() 能原样读回；token 会被 axios 拦截器取走放进请求头）
    applyLogin(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    /** 拉取用户上下文（家庭 + 成员列表） */
    async fetchContext() {
      const data = await authApi.me()
      this.user = data.user
      this.family = data.family
      this.members = data.members || []
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    async logout() {
      try {
        await authApi.logout()
      } catch {
        /* 忽略退出接口失败（如 token 已失效） */
      }
      // 必须先清 localStorage 再 $reset：$reset 会重新执行 state()（内部读取 localStorage），
      // 顺序颠倒会让内存态回填刚删过的旧身份，造成「退出后 store 仍残留登录态」
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      this.$reset()
    },
  },
})
