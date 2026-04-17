/**
 * SSE 流式请求工具
 * 使用 fetch + ReadableStream 处理 SSE 响应
 */

/**
 * 发起 SSE 流式请求
 * @param {string} url - 请求地址
 * @param {Object} callbacks - 回调函数集合
 * @param {function(string)} onMessage - 收到一条消息
 * @param {function()} onComplete - 流结束
 * @param {function(Error)} onError - 出错
 * @returns {AbortController} 用于取消请求
 */
export function fetchSSE(url, { onMessage, onComplete, onError }) {
  const controller = new AbortController()

  fetch(url, {
    method: 'GET',
    signal: controller.signal,
    headers: {
      'Accept': 'text/event-stream'
    }
  })
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            // 处理剩余 buffer
            if (buffer.trim()) {
              parseSSELines(buffer).forEach(line => {
                if (line) onMessage(line)
              })
            }
            onComplete && onComplete()
            return
          }

          buffer += decoder.decode(value, { stream: true })

          // 按 \n\n 分割 SSE 事件
          const parts = buffer.split('\n\n')
          buffer = parts.pop() // 最后一部分可能不完整

          for (const part of parts) {
            const data = parseSSEEvent(part)
            if (data !== null) {
              onMessage(data)
            }
          }

          read()
        }).catch(err => {
          if (err.name === 'AbortError') return
          onError && onError(err)
        })
      }

      read()
    })
    .catch(err => {
      if (err.name === 'AbortError') return
      onError && onError(err)
    })

  return controller
}

/**
 * 解析单个 SSE 事件块，提取 data 字段
 * SSE 规范：同一事件中的多个 data: 行用 \n 拼接
 */
function parseSSEEvent(chunk) {
  const lines = chunk.split('\n')
  const dataParts = []
  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataParts.push(line.slice(5))
    }
  }
  if (dataParts.length === 0) return null
  if (dataParts.length === 1 && dataParts[0].trim() === '[DONE]') return null
  return dataParts.join('\n')
}

/**
 * 解析多行 SSE 文本（流结束时残留 buffer）
 */
function parseSSELines(text) {
  const results = []
  const events = text.split('\n\n')
  for (const event of events) {
    if (!event.trim()) continue
    const data = parseSSEEvent(event)
    if (data) results.push(data)
  }
  return results
}

/**
 * 通过 POST multipart/form-data 发起 SSE 流式请求
 * 用于图片上传 + SSE 流式响应场景
 * @param {string} url - 请求地址
 * @param {FormData} formData - 表单数据
 * @param {Object} callbacks - 回调函数集合
 * @returns {AbortController} 用于取消请求
 */
export function fetchMultipartSSE(url, formData, { onMessage, onComplete, onError }) {
  const controller = new AbortController()

  fetch(url, {
    method: 'POST',
    body: formData,
    signal: controller.signal,
    headers: {
      'Accept': 'text/event-stream'
    }
  })
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            if (buffer.trim()) {
              parseSSELines(buffer).forEach(line => {
                if (line) onMessage(line)
              })
            }
            onComplete && onComplete()
            return
          }

          buffer += decoder.decode(value, { stream: true })
          const parts = buffer.split('\n\n')
          buffer = parts.pop()

          for (const part of parts) {
            const data = parseSSEEvent(part)
            if (data !== null) {
              onMessage(data)
            }
          }

          read()
        }).catch(err => {
          if (err.name === 'AbortError') return
          onError && onError(err)
        })
      }

      read()
    })
    .catch(err => {
      if (err.name === 'AbortError') return
      onError && onError(err)
    })

  return controller
}
