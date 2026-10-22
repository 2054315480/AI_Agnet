import { get, request } from './http.js'

const API_BASE = '/api'

/** 获取总览统计（会话数/意图分布/转人工率/平均轮次等）。 */
export function getOverview() {
  return get('/analytics/overview')
}

/** 获取意图分布统计。 */
export function getIntentDistribution() {
  return get('/analytics/intent-distribution')
}

/** 获取会话列表（分页 + 排序 + 筛选）。 */
export function getSessionList(params = {}) {
  return get('/analytics/sessions', params)
}

/** 获取单会话详情（完整对话回放）。 */
export function getSessionDetail(conversationId) {
  return get(`/analytics/sessions/${encodeURIComponent(conversationId)}`)
}

/** 全量导出 CSV 地址。 */
export function getExportAllCsvUrl() {
  return `${API_BASE}/analytics/export/csv`
}

/** 单会话导出 CSV 地址。 */
export function getExportSessionCsvUrl(conversationId) {
  return `${API_BASE}/analytics/export/csv/${encodeURIComponent(conversationId)}`
}

/** 带鉴权下载 CSV，返回 Blob。 */
export function downloadCsv(path) {
  return request(path, { responseType: 'blob' })
}
