import request from '../utils/request'

/**
 * 分析接口模块（“系统分析”页的核心数据源）：环比/同比对比、异常规则提醒、月度文字报告。
 * Analysis.vue 调用全部三个接口；其中 anomalies（R1~R7 异常提醒）
 * 也被 Dashboard.vue 与 Layout.vue（顶栏提醒铃铛）复用。
 */

/** 环比/同比（month: yyyy-MM，空=当月） */
export const compare = (month) => request.get('/analysis/compare', { params: { month } })

/** 异常与关注项（R1~R7 规则） */
export const anomalies = (month) => request.get('/analysis/anomalies', { params: { month } })

/** 月度分析报告（文本） */
export const report = (month) => request.get('/analysis/report', { params: { month } })
