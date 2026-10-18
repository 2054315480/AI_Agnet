import { fetchSSE, fetchMultipartSSE } from '../utils/sse.js'
import { useAuth } from './auth.js'

const API_BASE = '/api'

function authHeaders() {
  const { token } = useAuth()
  if (token.value) {
    return { 'Authorization': `Bearer ${token.value}` }
  }
  return {}
}

/**
 * 智能客服 SSE 流式对话
 * 事件模式：每个 data 是 JSON，包含 type 字段（intent/clarification/handoff/tool_call/tool_result/answer/done/session_id）
 */
export function chatCustomerServiceStream(message, sessionId, { onStep, onComplete, onError }) {
  let url = `${API_BASE}/customer-service/chat?message=${encodeURIComponent(message)}`
  if (sessionId) {
    url += `&sessionId=${encodeURIComponent(sessionId)}`
  }
  return fetchSSE(url, {
    onMessage: onStep,
    onComplete,
    onError,
    headers: authHeaders()
  })
}

/**
 * 结束会话：后端会把该 session 的状态置为 CLOSED，下次同 sessionId 提问会重新初始化
 */
export async function closeSession(sessionId) {
  const res = await fetch(`${API_BASE}/customer-service/session/${encodeURIComponent(sessionId)}/close`, {
    method: 'POST',
    headers: authHeaders()
  })
  if (!res.ok) {
    throw new Error('HTTP ' + res.status)
  }
  return res.json().catch(() => ({}))
}
