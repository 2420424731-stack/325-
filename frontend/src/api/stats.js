import request from '../utils/request'

/**
 * 统计接口模块（只读聚合查询，不做任何修改）：按时间、分类、成员、商家、片区、标签等维度汇总流水。
 * Dashboard.vue（首页仪表盘）使用 overview / trend / category；
 * Analysis.vue（统计分析页）使用 trend / category / merchant / region。
 */

/** 月度/年度收支汇总（month 空=整年） */
export const statsOverview = (params) => request.get('/stats/overview', { params })

/** 近 N 个月收支趋势 */
export const statsTrend = (months = 12) => request.get('/stats/trend', { params: { months } })

/** 分类汇总 */
export const statsCategory = (params) => request.get('/stats/category', { params })

/** 按成员汇总 */
export const statsMember = (params) => request.get('/stats/member', { params })

/** 商家 Top N */
export const statsMerchant = (params) => request.get('/stats/merchant', { params })

/** 片区分布 */
export const statsRegion = (params) => request.get('/stats/region', { params })

/** 标签汇总 */
export const statsTags = (params) => request.get('/stats/tags', { params })
