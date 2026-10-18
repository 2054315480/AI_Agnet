<template>
  <Teleport to="body">
    <Transition name="drawer-overlay">
      <div v-if="open" class="drawer-overlay" @click="$emit('close')"></div>
    </Transition>
    <Transition name="drawer-panel">
      <aside
        v-if="open"
        class="chat-drawer"
        role="dialog"
        aria-label="会话信息"
        tabindex="-1"
        ref="panelRef"
        @keydown.esc="$emit('close')"
      >
        <!-- 头部 -->
        <header class="drawer-head">
          <div class="head-copy">
            <div class="head-eyebrow">Session</div>
            <h3 class="head-title">会话信息</h3>
          </div>
          <button class="icon-btn" aria-label="关闭" @click="$emit('close')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </header>

        <div class="drawer-scroll">
          <!-- 概要 -->
          <section class="d-section">
            <div class="d-label">概要</div>
            <div class="kv">
              <span class="k">标题</span>
              <span class="v">{{ conversation?.title || '未命名会话' }}</span>
            </div>
            <div class="kv">
              <span class="k">会话 ID</span>
              <span class="v mono">
                {{ shortId }}
                <button class="mini-btn" title="复制会话 ID" @click="copyId">复制</button>
              </span>
            </div>
            <div class="kv">
              <span class="k">创建时间</span>
              <span class="v tnum">{{ formatTime(conversation?.createdAt) }}</span>
            </div>
            <div class="kv">
              <span class="k">最近活跃</span>
              <span class="v tnum">{{ formatTime(conversation?.updatedAt) }}</span>
            </div>
            <div class="kv">
              <span class="k">消息</span>
              <span class="v tnum">{{ userTurns }} 轮提问 · {{ answerCount }} 条答复</span>
            </div>
          </section>

          <!-- 会话状态 -->
          <section class="d-section">
            <div class="d-label">状态</div>
            <div class="chips">
              <span class="chip" :class="{ 'chip-love': handoff }">
                {{ handoff ? '已转人工' : '机器人在线' }}
              </span>
              <span class="chip" v-if="lastIntent">
                {{ lastIntent.displayName || lastIntent.intent }}
                <em class="chip-num">{{ Math.round((lastIntent.confidence || 0) * 100) }}%</em>
              </span>
              <span class="chip" v-if="clarifications > 0">澄清 {{ clarifications }} 次</span>
            </div>
          </section>

          <!-- 来源 -->
          <section class="d-section">
            <div class="d-label">
              引用来源
              <span class="d-count">{{ sources.length }}</span>
            </div>
            <ul v-if="sources.length" class="source-list">
              <li v-for="s in sources" :key="s.name" class="source-item">
                <span class="source-dot"></span>
                <span class="source-name" :title="s.name">{{ s.name }}</span>
                <span class="source-count tnum" v-if="s.count > 1">×{{ s.count }}</span>
              </li>
            </ul>
            <p v-else class="d-empty">本次会话还没有引用来源。命中知识库后，这里会列出原文出处。</p>
          </section>

          <!-- 处理过程 -->
          <section class="d-section">
            <div class="d-label">
              处理过程
              <span class="d-count">{{ steps.length }}</span>
            </div>
            <ol v-if="steps.length" class="step-list">
              <li v-for="(step, i) in steps" :key="i" class="step-item">
                <span class="step-type mono">{{ step.type }}</span>
                <span class="step-text">{{ step.content }}</span>
              </li>
            </ol>
            <p v-else class="d-empty">工具调用记录只在实时对话中展示，历史会话不保留该明细。</p>
          </section>

          <!-- 操作 -->
          <section class="d-section d-actions">
            <button class="act-btn" @click="exportMarkdown">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              导出为 Markdown
            </button>
            <button class="act-btn danger" :disabled="closing" @click="endSession">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9"/><line x1="8" y1="12" x2="16" y2="12"/>
              </svg>
              {{ closing ? '正在结束…' : '结束本次会话' }}
            </button>
            <p v-if="notice" class="d-notice">{{ notice }}</p>
          </section>
        </div>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { closeSession } from '../api/chat.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  conversation: { type: Object, default: null }
})
const emit = defineEmits(['close', 'ended'])

const panelRef = ref(null)
const closing = ref(false)
const notice = ref('')

const messages = computed(() => props.conversation?.messages || [])
const userTurns = computed(() => messages.value.filter(m => m.isUser).length)
const answerCount = computed(() => messages.value.filter(m => !m.isUser && !m.loading).length)
const sessionId = computed(() => props.conversation?.id || '')
const shortId = computed(() => {
  const id = sessionId.value
  if (!id) return '-'
  return id.length > 18 ? id.slice(0, 8) + '…' + id.slice(-6) : id
})

const sources = computed(() => {
  const map = new Map()
  for (const m of messages.value) {
    for (const s of (m.sources || [])) {
      map.set(s, (map.get(s) || 0) + 1)
    }
  }
  return [...map.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
})

const lastIntent = computed(() => {
  const hit = [...messages.value].reverse().find(m => m.intentInfo)
  return hit ? hit.intentInfo : null
})

const handoff = computed(() => messages.value.some(m => m.isHandoff))
const clarifications = computed(() => messages.value.filter(m => m.isClarification).length)

const steps = computed(() => {
  const hit = [...messages.value].reverse().find(m => (m.thinkingSteps || []).length)
  return hit ? hit.thinkingSteps : []
})

function formatTime(ts) {
  if (!ts) return '-'
  try {
    return new Date(ts).toLocaleString('zh-CN', { hour12: false })
  } catch (e) {
    return '-'
  }
}

async function copyId() {
  const id = sessionId.value
  if (!id) return
  try {
    await navigator.clipboard.writeText(id)
    notice.value = '会话 ID 已复制'
  } catch (e) {
    notice.value = '复制失败，请手动选择'
  }
  setTimeout(() => { notice.value = '' }, 1800)
}

function exportMarkdown() {
  const conv = props.conversation
  if (!conv) return
  const lines = [
    '# ' + (conv.title || '客服会话'),
    '',
    '- 会话 ID：' + sessionId.value,
    '- 导出时间：' + new Date().toLocaleString('zh-CN', { hour12: false }),
    ''
  ]
  for (const m of messages.value) {
    lines.push('## ' + (m.isUser ? '用户' : '客服') + (m.time ? ' · ' + m.time : ''))
    lines.push('')
    lines.push(m.content || '')
    if ((m.sources || []).length) {
      lines.push('')
      lines.push('> 出处：' + m.sources.join('、'))
    }
    lines.push('')
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (conv.title || '客服会话') + '.md'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  notice.value = '已导出 Markdown'
  setTimeout(() => { notice.value = '' }, 1800)
}

async function endSession() {
  if (!sessionId.value) return
  closing.value = true
  try {
    await closeSession(sessionId.value)
    notice.value = '会话已结束，下次提问会开启新会话'
    emit('ended')
  } catch (e) {
    notice.value = '结束会话失败：' + (e.message || '未知错误')
  } finally {
    closing.value = false
  }
}

// 打开时把焦点移到面板，便于 Esc 关闭
watch(() => props.open, async (val) => {
  if (val) {
    await nextTick()
    panelRef.value?.focus()
  } else {
    notice.value = ''
  }
})
</script>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  background: var(--bg-overlay);
  backdrop-filter: blur(1px);
}

.chat-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: calc(var(--z-overlay) + 1);
  width: 340px;
  max-width: 92vw;
  display: flex;
  flex-direction: column;
  background: var(--bg-sidebar);
  border-left: 1px solid var(--rule);
  outline: none;
}

@media (min-width: 1100px) {
  /* 宽屏时不遮住对话，直接作为右侧栏推入 */
  .drawer-overlay { display: none; }
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--rule);
  flex-shrink: 0;
}

.head-eyebrow {
  font-size: 0.5625rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  margin-bottom: 3px;
}

.head-title {
  font-family: var(--font-serif);
  font-size: 1.0625rem;
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.icon-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xs);
  color: var(--text-tertiary);
  transition: background var(--duration-fast) var(--ease-out),
              color var(--duration-fast) var(--ease-out);
}

.icon-btn:hover { background: var(--bg-hover); color: var(--text-primary); }

.drawer-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 4px 16px 24px;
}

.d-section {
  padding: 14px 0;
  border-bottom: 1px solid var(--rule);
}

.d-section:last-child { border-bottom: 0; }

.d-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.625rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  margin-bottom: 10px;
}

.d-count {
  font-family: var(--font-mono);
  font-size: 0.5625rem;
  padding: 0 4px;
  border: 1px solid var(--border-secondary);
  border-radius: 3px;
  color: var(--text-tertiary);
}

.kv {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
  font-size: 0.8125rem;
}

.kv .k { color: var(--text-tertiary); flex-shrink: 0; }

.kv .v {
  color: var(--text-secondary);
  text-align: right;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kv .v.mono { font-family: var(--font-mono); font-size: 0.75rem; }
.tnum { font-variant-numeric: tabular-nums; }

.mini-btn {
  margin-left: 6px;
  padding: 0 5px;
  font-size: 0.625rem;
  line-height: 1.6;
  border: 1px solid var(--border-secondary);
  border-radius: 3px;
  color: var(--text-tertiary);
  transition: all var(--duration-fast) var(--ease-out);
}

.mini-btn:hover { color: var(--accent); border-color: var(--accent-border); }

.chips { display: flex; flex-wrap: wrap; gap: 6px; }

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-full);
  font-size: 0.6875rem;
  color: var(--text-secondary);
}

.chip-love {
  border-color: var(--accent-love-border);
  background: var(--accent-love-bg);
  color: var(--accent-love);
}

.chip-num { font-family: var(--font-mono); font-style: normal; opacity: 0.7; }

.source-list, .step-list { list-style: none; }

.source-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.source-dot {
  width: 4px; height: 4px;
  border-radius: 50%;
  background: var(--accent);
  flex-shrink: 0;
}

.source-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-count { font-size: 0.6875rem; color: var(--text-tertiary); }

.step-item {
  position: relative;
  padding: 4px 0 4px 12px;
  border-left: 1px solid var(--rule);
  margin-left: 2px;
}

.step-type {
  display: block;
  font-size: 0.5625rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  margin-bottom: 1px;
}

.step-text {
  font-size: 0.75rem;
  line-height: 1.6;
  color: var(--text-secondary);
  word-break: break-all;
}

.d-empty {
  font-size: 0.75rem;
  line-height: 1.7;
  color: var(--text-tertiary);
}

.d-actions { display: flex; flex-direction: column; gap: 8px; }

.act-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.8125rem;
  text-align: left;
  transition: all var(--duration-fast) var(--ease-out);
}

.act-btn:hover {
  border-color: var(--accent-border);
  color: var(--text-primary);
  background: var(--bg-hover);
}

.act-btn.danger:hover {
  border-color: var(--danger-border);
  background: var(--danger-bg);
  color: var(--danger);
}

.act-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.d-notice {
  margin-top: 2px;
  font-size: 0.6875rem;
  color: var(--accent);
}

/* 动效 */
.drawer-panel-enter-active,
.drawer-panel-leave-active { transition: transform 260ms var(--ease-out); }
.drawer-panel-enter-from,
.drawer-panel-leave-to { transform: translateX(100%); }

.drawer-overlay-enter-active,
.drawer-overlay-leave-active { transition: opacity 200ms var(--ease-out); }
.drawer-overlay-enter-from,
.drawer-overlay-leave-to { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .drawer-panel-enter-active, .drawer-panel-leave-active,
  .drawer-overlay-enter-active, .drawer-overlay-leave-active { transition: none; }
}
</style>
