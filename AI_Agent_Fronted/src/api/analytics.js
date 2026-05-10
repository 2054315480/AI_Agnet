import axios from 'axios'

const API_BASE = '/api'

function getAuthHeaders() {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 获取总览统计（会话数/意图分布/转人工率/平均轮次等） */
export async function getOverview() {
  const res = await axios.get(`${API_BASE}/analytics/overview`, { headers: getAuthHeaders() })
  return res.data
}

/** 获取意图分布统计 */
export async function getIntentDistribution() {
  const res = await axios.get(`${API_BASE}/analytics/intent-distribution`, { headers: getAuthHeaders() })
  return res.data
}

/** 获取会话列表（分页 + 排序 + 筛选） */
export async function getSessionList(params = {}) {
  const res = await axios.get(`${API_BASE}/analytics/sessions`, {
    headers: getAuthHeaders(),
    params
  })
  return res.data
}

/** 获取单会话详情（完整对话回放） */
export async function getSessionDetail(conversationId) {
  const res = await axios.get(`${API_BASE}/analytics/sessions/${conversationId}`, { headers: getAuthHeaders() })
  return res.data
}

/** 全量导出 CSV 地址 */
export function getExportAllCsvUrl() {
  return `${API_BASE}/analytics/export/csv`
}

/** 单会话导出 CSV 地址 */
export function getExportSessionCsvUrl(conversationId) {
  return `${API_BASE}/analytics/export/csv/${conversationId}`
}
