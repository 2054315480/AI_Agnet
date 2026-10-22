import { fetchSSE } from '../utils/sse.js'
import { request, authHeaders } from './http.js'

/**
 * 智能客服 SSE 流式对话。
 * 事件模式下每条 data 都是 JSON，包含 type 字段。
 */
export function chatCustomerServiceStream(message, sessionId, { onStep, onComplete, onError }) {
  let url = `/api/customer-service/chat?message=${encodeURIComponent(message)}`
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

/** 结束会话。 */
export function closeSession(sessionId) {
  return request(`/customer-service/session/${encodeURIComponent(sessionId)}/close`, {
    method: 'POST'
  })
}
