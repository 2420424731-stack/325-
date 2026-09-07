import dayjs from 'dayjs'

/** 金额格式化：千分位 + 两位小数（设计文档 14.2：前端展示两位小数） */
export function money(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 百分比（后端返回数值，如 31.5 表示 31.5%；null 无意义时返回 '--'） */
export function pct(v) {
  if (v === null || v === undefined) return '--'
  return `${Number(v).toFixed(1)}%`
}

/** 带正负号的百分比（正增长带 +） */
export function signedPct(v) {
  if (v === null || v === undefined) return '--'
  const n = Number(v)
  return `${n > 0 ? '+' : ''}${n.toFixed(1)}%`
}

/** 日期 yyyy-MM-dd（null 返回 '--'） */
export function date(v) {
  return v ? dayjs(v).format('YYYY-MM-DD') : '--'
}

/** 日期时间 yyyy-MM-dd HH:mm（null 返回 '--'） */
export function datetime(v) {
  return v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '--'
}

/** 当前月份 yyyy-MM */
export function currentMonth() {
  return dayjs().format('YYYY-MM')
}

/** 本月首日 yyyy-MM-dd */
export function monthStart(month) {
  return dayjs(month).startOf('month').format('YYYY-MM-DD')
}

/** 本月末日 yyyy-MM-dd */
export function monthEnd(month) {
  return dayjs(month).endOf('month').format('YYYY-MM-DD')
}
