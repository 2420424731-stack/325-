import request from '../utils/request'

/**
 * 收支流水接口模块：家庭账本核心记录的查询、导出与增删改。
 * 被 Transactions.vue（收支管理页：分页筛选/记一笔/CSV 导出）调用，
 * Dashboard.vue（首页仪表盘）也用它取最近流水。
 */

/** 分页 + 多条件查询 */
export const pageTransactions = (params) => request.get('/transactions', { params })

/** 详情 */
export const getTransaction = (id) => request.get(`/transactions/${id}`)

/** 按筛选条件导出 CSV */
export const exportTransactions = (params) => request.get('/transactions/export', { params })

/** 新增一笔流水（“记一笔”提交） */
export const createTransaction = (data) => request.post('/transactions', data)
/** 修改流水 / 删除流水 */
export const updateTransaction = (id, data) => request.put(`/transactions/${id}`, data)
export const deleteTransaction = (id) => request.delete(`/transactions/${id}`)
