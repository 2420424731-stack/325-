import request from '../utils/request'

export const listLoans = () => request.get('/loans')
export const createLoan = (data) => request.post('/loans', data)
export const updateLoan = (id, data) => request.put(`/loans/${id}`, data)
export const deleteLoan = (id) => request.delete(`/loans/${id}`)

/** 还款计划测算（等额本息/等额本金逐月明细） */
export const loanPlan = (id) => request.get(`/loans/${id}/plan`)
