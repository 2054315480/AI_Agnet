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
 */
function parseSSEEvent(chunk) {
  const lines = chunk.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (data === '[DONE]') return null
      return data
    }
  }
  return null
}

/**
 * 解析多行 SSE 文本
 */
function parseSSELines(text) {
  const results = []
  const lines = text.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (data && data !== '[DONE]') {
        results.push(data)
      }
    }
  }
  return results
}
