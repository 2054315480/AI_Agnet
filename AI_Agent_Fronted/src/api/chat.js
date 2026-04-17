import { fetchSSE, fetchMultipartSSE } from '../utils/sse.js'

const API_BASE = '/api'

/**
 * 恋爱大师 SSE 流式对话
 * 文本碎片模式：每个 data 是一个文本碎片，前端在同一个气泡中拼接
 */
export function chatLoveStream(message, chatId, { onChunk, onComplete, onError }) {
  const url = `${API_BASE}/ai/love_app/chat/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`
  return fetchSSE(url, {
    onMessage: onChunk,
    onComplete,
    onError
  })
}

/**
 * 恋爱大师 SSE 流式对话（支持图片上传）
 * 使用 POST multipart/form-data
 */
export function chatLoveStreamWithImage(message, chatId, imageFile, { onChunk, onComplete, onError }) {
  const url = `${API_BASE}/ai/love_app/chat/sse_with_image`
  const formData = new FormData()
  formData.append('message', message)
  formData.append('chatId', chatId)
  if (imageFile) {
    formData.append('image', imageFile)
  }
  return fetchMultipartSSE(url, formData, {
    onMessage: onChunk,
    onComplete,
    onError
  })
}

/**
 * 超级智能体 SSE 流式对话
 * 步骤模式：每个 data 是一个步骤的结果，前端每条单独一个气泡
 */
export function chatManusStream(message, { onStep, onComplete, onError }) {
  const url = `${API_BASE}/ai/manus/chat?message=${encodeURIComponent(message)}`
  return fetchSSE(url, {
    onMessage: onStep,
    onComplete,
    onError
  })
}

/**
 * 超级智能体 SSE 流式对话（支持图片上传）
 * 使用 POST multipart/form-data
 */
export function chatManusStreamWithImage(message, imageFile, { onStep, onComplete, onError }) {
  const url = `${API_BASE}/ai/manus/chat_with_image`
  const formData = new FormData()
  formData.append('message', message)
  if (imageFile) {
    formData.append('image', imageFile)
  }
  return fetchMultipartSSE(url, formData, {
    onMessage: onStep,
    onComplete,
    onError
  })
}
