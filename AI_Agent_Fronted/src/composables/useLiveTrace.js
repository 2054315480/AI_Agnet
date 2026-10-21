import { ref, computed } from 'vue'

/**
 * 实时链路状态：由 SSE 的 span 事件驱动，供右侧实时面板渲染。
 *
 * 设计要点：
 * 1. 按 span 名称维护条目，phase=start 先占位（显示"进行中"），phase=end 补耗时
 * 2. 只保留最近 30 个 span，避免长会话内存膨胀
 * 3. 每次新请求开始时 reset()，保证面板只显示当前这一轮
 */
const spans = ref([])
const metrics = ref({})
const running = ref(false)

export function useLiveTrace() {
  function reset() {
    spans.value = []
    metrics.value = {}
    running.value = true
  }

  function applySpan(payload) {
    let data
    try {
      data = typeof payload === 'string' ? JSON.parse(payload) : payload
    } catch (e) {
      return
    }
    if (!data || !data.name) return

    const key = data.name
    const idx = spans.value.findIndex(s => s.name === key)
    const entry = {
      name: key,
      phase: data.phase,
      offsetMs: data.offsetMs || 0,
      durationMs: data.durationMs || 0,
      status: data.status || 'running',
      attrs: data.attrs || {}
    }
    if (idx >= 0) {
      spans.value.splice(idx, 1, entry)
    } else {
      spans.value.push(entry)
    }
    if (spans.value.length > 30) {
      spans.value.splice(0, spans.value.length - 30)
    }
  }

  function applyMetrics(payload) {
    let data
    try {
      data = typeof payload === 'string' ? JSON.parse(payload) : payload
    } catch (e) {
      return
    }
    if (data && typeof data === 'object') {
      metrics.value = { ...metrics.value, ...data }
    }
  }

  function finish() {
    running.value = false
  }

  const totalMs = computed(() => {
    let max = 0
    for (const s of spans.value) {
      const end = (s.offsetMs || 0) + (s.durationMs || 0)
      if (end > max) max = end
    }
    return max
  })

  return { spans, metrics, running, totalMs, reset, applySpan, applyMetrics, finish }
}
