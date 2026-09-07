import request from '../utils/request'

/** 分页 + 多条件查询 */
export const pageTransactions = (params) => request.get('/transactions', { params })

/** 详情 */
export const getTransaction = (id) => request.get(`/transactions/${id}`)

export const createTransaction = (data) => request.post('/transactions', data)
export const updateTransaction = (id, data) => request.put(`/transactions/${id}`, data)
export const deleteTransaction = (id) => request.delete(`/transactions/${id}`)
