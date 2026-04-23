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
