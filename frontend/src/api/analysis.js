import request from '../utils/request'

/** 环比/同比（month: yyyy-MM，空=当月） */
export const compare = (month) => request.get('/analysis/compare', { params: { month } })

/** 异常与关注项（R1~R7 规则） */
export const anomalies = (month) => request.get('/analysis/anomalies', { params: { month } })

/** 月度分析报告（文本） */
export const report = (month) => request.get('/analysis/report', { params: { month } })
