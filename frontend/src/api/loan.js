import request from '../utils/request'

/**
 * 贷款接口模块：家庭贷款（房贷/车贷等）的增删改查与还款计划测算。
 * 增删改查由 Assets.vue（资产管理页的贷款 Tab）调用；
 * listLoans 还被 Layout.vue 顶栏提醒铃铛使用（统计本月有应还贷款）。
 */

/** 当前家庭全部贷款 */
export const listLoans = () => request.get('/loans')
/** 新增贷款记录 */
export const createLoan = (data) => request.post('/loans', data)
/** 修改贷款信息 / 结清或注销贷款 */
export const updateLoan = (id, data) => request.put(`/loans/${id}`, data)
export const deleteLoan = (id) => request.delete(`/loans/${id}`)

/** 还款计划测算（等额本息/等额本金逐月明细） */
export const loanPlan = (id) => request.get(`/loans/${id}/plan`)
