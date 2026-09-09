import request from '../utils/request'

/**
 * 成员接口模块：家庭成员的增删改查。
 * 由 Members.vue（成员管理页）调用；系统登录后还会把成员列表经 /auth/me
 * 拉入 user store（stores/user.js），供收支归属、统计筛选等处的下拉选择使用。
 */

/** 当前家庭全部成员 */
export const listMembers = () => request.get('/members')
/** 新增成员（邀请或直接录入） */
export const addMember = (data) => request.post('/members', data)
/** 修改成员信息（昵称/角色等） */
export const updateMember = (id, data) => request.put(`/members/${id}`, data)
/** 移出成员（其名下流水会如何处理由后端规则决定） */
export const deleteMember = (id) => request.delete(`/members/${id}`)
