import request from '../utils/request'

/**
 * 认证接口模块：注册 / 登录 / 查询当前用户 / 退出 / 修改密码。
 * 主要被 stores/user.js（login/register/logout/fetchContext）调用，
 * Profile.vue（个人中心）会直接用其中的 changePassword。
 * 请求经过 utils/request.js 封装，自动携带 token 并统一解包。
 */

/** 注册（自动建家庭、户主成员与内置分类，成功后自动登录） */
export const register = (data) => request.post('/auth/register', data)

/** 登录 */
export const login = (data) => request.post('/auth/login', data)

/** 当前用户上下文：用户 + 家庭 + 成员列表 */
export const me = () => request.get('/auth/me')

/** 退出登录 */
export const logout = () => request.post('/auth/logout')

/** 修改密码 */
export const changePassword = (data) => request.put('/auth/password', data)
