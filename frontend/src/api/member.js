import request from '../utils/request'

export const listMembers = () => request.get('/members')
export const addMember = (data) => request.post('/members', data)
export const updateMember = (id, data) => request.put(`/members/${id}`, data)
export const deleteMember = (id) => request.delete(`/members/${id}`)
