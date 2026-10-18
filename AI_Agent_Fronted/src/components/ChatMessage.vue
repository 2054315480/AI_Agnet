<template>
  <div class="chat-message" :class="[isUser ? 'user' : 'ai', `agent-${agent}`]">
    <!-- Avatar -->
    <div class="avatar" :class="{ 'user-avatar': isUser }" aria-hidden="true">
      <span class="avatar-glyph">{{ isUser ? '我' : '客' }}</span>
    </div>
    <!-- Bubble -->
    <div class="bubble-wrap">
      <!-- 助手消息头：名称与时间放在顶部，更接近工单/记录式排版 -->
      <div v-if="!isUser" class="msg-head">
        <span class="msg-author">客服工作台</span>
        <span v-if="time" class="msg-head-time">{{ time }}</span>
      </div>

      <!-- Intent tag -->
      <div v-if="intentInfo && !isUser" class="intent-tag" :class="intentTagClass">
        {{ intentInfo.displayName || intentInfo.intent }}
        <span class="intent-confidence">{{ Math.round((intentInfo.confidence || 0) * 100) }}%</span>
      </div>

      <!-- Handoff alert -->
      <div v-if="isHandoff && !isUser" class="handoff-alert">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
          <circle cx="9" cy="7" r="4"/>
          <line x1="19" y1="8" x2="19" y2="14"/>
          <line x1="22" y1="11" x2="16" y2="11"/>
        </svg>
        <span>转接人工客服中...</span>
      </div>

      <!-- 澄清选项卡片 -->
      <div v-if="isClarification && !isUser && clarificationOptions.length > 0 && !loading" class="clarification-card">
        <div class="clarification-title">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          <span>请选择您想了解的内容：</span>
        </div>
        <button
          v-for="(opt, idx) in clarificationOptions"
          :key="idx"
          class="clarification-option"
          @click="$emit('ask-question', opt)"
        >{{ opt }}</button>
      </div>

      <!-- 来源标注 -->
      <div v-if="!isUser && sources.length > 0 && !loading" class="source-citations">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
        </svg>
        <span v-for="(s, idx) in sources" :key="idx" class="source-tag">{{ s }}</span>
      </div>

      <!-- Thinking process block (DeepSeek style) -->
      <div v-if="hasThinkingSteps" class="thinking-block" :class="{ expanded: isThinkingExpanded }">
        <button class="thinking-header" @click="toggleThinking">
          <svg class="thinking-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
          <!-- Spinning icon when loading, static when done -->
          <svg v-if="loading" class="thinking-icon spinning" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
          <svg v-else class="thinking-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 16v-4"/>
            <path d="M12 8h.01"/>
          </svg>
          <span class="thinking-title">
            <template v-if="loading">
              正在处理 · {{ thinkingSteps.length }} 步
            </template>
            <template v-else>
              处理过程 · {{ thinkingSteps.length }} 步
            </template>
          </span>
        </button>

        <!-- Thinking steps body -->
        <div class="thinking-body" ref="thinkingBodyRef">
          <div class="thinking-body-inner">
            <div
              v-for="(step, idx) in displayedSteps"
              :key="idx"
              class="thinking-step"
              :class="[`step-${step.type}`, { 'step-new': idx === thinkingSteps.length - 1 && loading }]"
            >
              <span class="step-icon">{{ stepIcon(step.type) }}</span>
              <div class="step-content">
                <span class="step-label-text">{{ stepLabel(step.type) }}</span>
                <span class="step-text">{{ step.displayContent }}</span>
              </div>
            </div>
            <!-- Loading dots at the end while thinking -->
            <div v-if="loading" class="thinking-loading">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- Main content bubble -->
      <div v-if="imageUrl || content || (loading && !hasThinkingSteps)" class="bubble" :class="{ 'is-loading': loading && !content }">
        <div v-if="imageUrl" class="bubble-image">
          <img :src="imageUrl" alt="用户上传的图片" @click="previewFullImage" />
        </div>
        <div
          v-if="content"
          class="bubble-text"
          :class="{ 'streaming-cursor': loading && content }"
          v-html="renderedContent"
        ></div>
        <div v-if="loading && !content && !hasThinkingSteps" class="typing-indicator">
          <span></span><span></span><span></span>
        </div>
      </div>
      <div v-if="time && isUser" class="msg-time">{{ time }}</div>
      <!-- Suggested questions -->
      <div v-if="!isUser && suggestedQuestions && suggestedQuestions.length > 0 && !loading" class="suggested-questions">
        <div class="suggested-label">继续追问</div>
        <button
          v-for="(q, idx) in suggestedQuestions"
          :key="idx"
          class="suggested-item"
          @click="$emit('ask-question', q)"
        >{{ q }}</button>
      </div>
    </div>

    <!-- Full-screen image preview overlay -->
    <Teleport to="body">
      <div v-if="showPreview" class="image-preview-overlay" @click="showPreview = false">
        <img :src="imageUrl" alt="预览" class="image-preview-full" @click.stop />
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true
})

const props = defineProps({
  content: { type: String, default: '' },
  isUser: { type: Boolean, default: false },
  agent: { type: String, default: 'customer_service' },
  loading: { type: Boolean, default: false },
  stepLabel: { type: String, default: '' },
  thinkingSteps: { type: Array, default: () => [] },
  imageUrl: { type: String, default: '' },
  time: { type: String, default: '' },
  intentInfo: { type: Object, default: null },
  isClarification: { type: Boolean, default: false },
  isHandoff: { type: Boolean, default: false },
  suggestedQuestions: { type: Array, default: () => [] },
  clarificationOptions: { type: Array, default: () => [] },
  sources: { type: Array, default: () => [] }
})

defineEmits(['ask-question'])

const thinkingOpen = ref(false)
const thinkingBodyRef = ref(null)
const showPreview = ref(false)

function previewFullImage() {
  showPreview.value = true
}

const hasThinkingSteps = computed(() => props.thinkingSteps && props.thinkingSteps.length > 0)

const intentTagClass = computed(() => {
  if (!props.intentInfo) return ''
  return props.intentInfo.intent === 'REQUEST_HUMAN' || props.intentInfo.intent === 'OUT_OF_SCOPE'
    ? 'intent-handoff' : ''
})

// Auto-expand during loading, allow manual toggle when done
const isThinkingExpanded = computed(() => {
  if (props.loading) return true  // Always expanded during streaming
  return thinkingOpen.value
})

function toggleThinking() {
  if (!props.loading) {
    thinkingOpen.value = !thinkingOpen.value
  }
}

// Truncate long content during streaming, show full when done
const TRUNCATE_LENGTH = 150
const displayedSteps = computed(() => {
  if (!props.thinkingSteps) return []
  return props.thinkingSteps.map((step, idx) => {
    const isLast = idx === props.thinkingSteps.length - 1
    // During loading, truncate all steps. When done, show full.
    if (props.loading && step.content && step.content.length > TRUNCATE_LENGTH) {
      return {
        ...step,
        displayContent: step.content.substring(0, TRUNCATE_LENGTH) + '...'
      }
    }
    return { ...step, displayContent: step.content }
  })
})

function stepIcon(type) {
  switch (type) {
    case 'thinking': return '\u{1F4AD}'
    case 'tool_call': return '\u{1F527}'
    case 'tool_result': return '\u{1F4CB}'
    default: return '\u{2022}'
  }
}

function stepLabel(type) {
  switch (type) {
    case 'thinking': return '思考中'
    case 'tool_call': return '调用工具'
    case 'tool_result': return '执行结果'
    default: return ''
  }
}

// Scroll thinking body to bottom when new steps arrive
watch(() => props.thinkingSteps?.length, () => {
  nextTick(() => {
    if (thinkingBodyRef.value) {
      thinkingBodyRef.value.scrollTop = thinkingBodyRef.value.scrollHeight
    }
  })
})

const renderedContent = computed(() => {
  if (!props.content) return ''
  if (props.isUser) {
    return props.content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br/>')
  }
  return marked.parse(props.content)
})
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  animation: fadeIn var(--duration-normal) var(--ease-out);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== User message ===== */
.chat-message.user {
  flex-direction: row-reverse;
}

.chat-message.user .bubble-wrap {
  align-items: flex-end;
}

.chat-message.user .bubble {
  background: var(--bg-bubble-user);
  color: var(--text-user-bubble);
  border-radius: 18px 18px 4px 18px;
}

.chat-message.user .msg-time {
  text-align: right;
}

/* ===== AI message ===== */
.chat-message.ai .bubble-wrap {
  align-items: flex-start;
}

.chat-message.ai .bubble {
  background: var(--bg-bubble-ai);
  border: 1px solid var(--border-secondary);
  border-radius: 18px 18px 18px 4px;
  color: var(--text-ai-bubble);
}

/* ===== Avatar ===== */
.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  margin-top: 2px;
}

.avatar svg {
  width: 100%;
  height: 100%;
}

/* ===== Bubble wrap ===== */
.bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: 75%;
  min-width: 0;
}

.bubble {
  padding: 10px 16px;
  line-height: 1.6;
  word-break: break-word;
}

.bubble-text {
  text-align: left;
  font-size: 0.9375rem;
  line-height: 1.7;
}

/* Streaming cursor */
.streaming-cursor::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 1em;
  background: var(--text-primary);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink-cursor 1s step-end infinite;
}

@keyframes blink-cursor {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ===== Intent tag ===== */
.intent-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  margin-bottom: 4px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  border: 1px solid var(--border-secondary);
}

.intent-tag.intent-handoff {
  background: var(--danger-bg);
  color: var(--danger);
  border-color: rgba(248, 81, 73, 0.2);
}

.intent-confidence {
  font-size: 0.65rem;
  opacity: 0.7;
}

/* ===== Handoff alert ===== */
.handoff-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  background: rgba(248, 81, 73, 0.08);
  border: 1px solid rgba(248, 81, 73, 0.2);
  color: var(--danger);
  font-size: 0.8125rem;
  font-weight: 500;
  margin-bottom: 6px;
  animation: pulseAlert 2s ease infinite;
}

@keyframes pulseAlert {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* ===== Clarification ===== */
.bubble.is-clarification {
  border-color: rgba(255, 193, 7, 0.3);
  background: rgba(255, 193, 7, 0.05);
}

/* 澄清选项卡片 — 结构化列表风格 */
.clarification-card {
  margin-bottom: 6px;
  padding: 0;
  border-radius: var(--radius-sm);
  background: var(--bg-sidebar);
  border: 1px solid var(--border-secondary);
  overflow: hidden;
}

.clarification-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-secondary);
}

.clarification-title svg {
  color: var(--accent-primary);
  flex-shrink: 0;
}

.clarification-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  padding: 10px 14px;
  border: none;
  border-bottom: 1px solid var(--border-secondary);
  background: transparent;
  color: var(--text-primary);
  font-size: 0.8125rem;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.clarification-option:last-child {
  border-bottom: none;
}

.clarification-option:hover {
  background: rgba(31, 111, 235, 0.04);
  color: var(--accent-primary);
}

/* 来源标注 — 简洁标签行 */
.source-citations {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 6px;
  padding: 4px 10px;
  border-radius: var(--radius-xs);
  border: 1px solid var(--border-secondary);
  background: var(--bg-sidebar);
}

.source-citations > svg {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.source-tag {
  font-size: 0.7rem;
  color: var(--text-secondary);
  background: var(--bg-hover);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
}

/* ===== Thinking process block (DeepSeek style) ===== */
.thinking-block {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-bottom: 8px;
  background: var(--bg-step);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  width: 100%;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-secondary);
  transition: var(--transition);
}

.thinking-header:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.thinking-chevron {
  flex-shrink: 0;
  transition: transform 0.25s ease;
}

.thinking-block.expanded .thinking-chevron {
  transform: rotate(180deg);
}

.thinking-icon {
  flex-shrink: 0;
  color: var(--accent-manus);
}

.thinking-icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.thinking-title {
  flex: 1;
  text-align: left;
}

/* Thinking body — scrollable when many steps */
.thinking-body {
  max-height: 0;
  overflow: hidden;
  transition: max-height var(--duration-normal) cubic-bezier(0.4, 0, 0.2, 1);
}

.thinking-block.expanded .thinking-body {
  max-height: 400px;
  overflow-y: auto;
}

.thinking-body-inner {
  padding: 0 14px 10px;
}

/* Individual step */
.thinking-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  font-size: 0.8125rem;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-secondary);
  line-height: 1.5;
  animation: stepSlideIn var(--duration-normal) var(--ease-out);
}

.thinking-step:last-child {
  border-bottom: none;
}

@keyframes stepSlideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Highlight the newest step during loading */
.thinking-step.step-new {
  color: var(--text-primary);
}

.step-icon {
  flex-shrink: 0;
  font-size: 0.875rem;
  width: 20px;
  text-align: center;
  padding-top: 1px;
}

.step-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.step-label-text {
  font-weight: 600;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: var(--text-tertiary);
}

.step-tool_call .step-label-text {
  color: var(--accent-manus);
}

.step-tool_result .step-label-text {
  color: var(--accent-primary);
}

.step-text {
  word-break: break-word;
  white-space: pre-wrap;
  font-size: 0.8125rem;
  line-height: 1.5;
}

/* Loading dots at end of thinking */
.thinking-loading {
  display: flex;
  gap: 4px;
  padding: 8px 0 0 28px;
}

.thinking-loading span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent-manus);
  animation: thinking-dot 1.4s infinite both;
}

.thinking-loading span:nth-child(2) { animation-delay: 0.2s; }
.thinking-loading span:nth-child(3) { animation-delay: 0.4s; }

@keyframes thinking-dot {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

/* ===== Markdown styles ===== */
.bubble-text :deep(code) {
  background: var(--bg-code);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.85em;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
}

.bubble-text :deep(h1),
.bubble-text :deep(h2),
.bubble-text :deep(h3),
.bubble-text :deep(h4) {
  margin: 12px 0 6px;
  font-weight: 700;
  line-height: 1.4;
}

.bubble-text :deep(h1) { font-size: 1.15em; }
.bubble-text :deep(h2) { font-size: 1.08em; }
.bubble-text :deep(h3) { font-size: 1.02em; }

.bubble-text :deep(p) {
  margin: 8px 0;
  line-height: 1.75;
}

.bubble-text :deep(p:first-child) {
  margin-top: 0;
}

.bubble-text :deep(p:last-child) {
  margin-bottom: 0;
}

.bubble-text :deep(ul),
.bubble-text :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}

.bubble-text :deep(li) {
  margin: 4px 0;
  line-height: 1.65;
}

.bubble-text :deep(li)::marker {
  color: var(--text-tertiary);
}

.bubble-text :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid var(--border-primary);
  color: var(--text-secondary);
}

.bubble-text :deep(pre) {
  margin: 8px 0;
  padding: 12px;
  border-radius: var(--radius-xs);
  background: var(--bg-code);
  overflow-x: auto;
  font-size: 0.85em;
  border: 1px solid var(--border-secondary);
}

.bubble-text :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 1em;
}

.bubble-text :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-primary);
  margin: 10px 0;
}

.bubble-text :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
  font-size: 0.9em;
}

.bubble-text :deep(th),
.bubble-text :deep(td) {
  border: 1px solid var(--border-primary);
  padding: 6px 10px;
  text-align: left;
}

.bubble-text :deep(th) {
  background: var(--bg-hover);
  font-weight: 600;
}

/* ===== Bubble image ===== */
.bubble-image {
  margin-bottom: 6px;
}

.bubble-image img {
  max-width: 200px;
  max-height: 150px;
  border-radius: 8px;
  cursor: pointer;
  object-fit: cover;
  transition: opacity 0.2s;
}

.bubble-image img:hover {
  opacity: 0.85;
}

/* ===== Full-screen image preview ===== */
.image-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  animation: fadeIn 0.2s ease;
}

.image-preview-full {
  max-width: 90vw;
  max-height: 90vh;
  border-radius: 8px;
  object-fit: contain;
}

/* ===== Time ===== */
.msg-time {
  font-size: 0.7rem;
  color: var(--text-tertiary);
  margin-top: 4px;
  padding: 0 4px;
}

/* ===== Typing indicator ===== */
.typing-indicator {
  display: inline-flex;
  gap: 4px;
  margin-left: 4px;
  vertical-align: middle;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
  animation: typing-bounce 1.4s infinite both;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing-bounce {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .chat-message {
    padding: 6px 0;
    gap: 8px;
  }

  .avatar {
    width: 32px;
    height: 32px;
  }

  .bubble-wrap {
    max-width: 80%;
  }

  .bubble {
    padding: 8px 12px;
  }
}

@media (max-width: 480px) {
  .bubble-wrap {
    max-width: 85%;
  }
}

/* ===== Suggested questions — 结构化列表 ===== */
.suggested-questions {
  margin-top: 6px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.suggested-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-tertiary);
  padding: 8px 12px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-secondary);
}

.suggested-item {
  text-align: left;
  width: 100%;
  padding: 9px 12px;
  border: none;
  border-bottom: 1px solid var(--border-secondary);
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.8125rem;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
  line-height: 1.5;
}

.suggested-item:last-child {
  border-bottom: none;
}

.suggested-item:hover {
  color: var(--accent-primary);
  background: rgba(31, 111, 235, 0.04);
}

/* ================================================================
   v6 覆写：案头风格
   ================================================================ */
.chat-message {
  animation: rise 280ms var(--ease-out) both;
}

.chat-message.ai { align-items: flex-start; }

/* 排版式头像：方形、发丝线、衬线字 */
.avatar {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  border: 1px solid var(--border-primary);
  background: var(--bg-step);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.avatar-glyph {
  font-family: var(--font-serif);
  font-size: 0.75rem;
  line-height: 1;
  color: var(--text-secondary);
}

.chat-message.user .avatar {
  background: var(--bg-bubble-user);
  border-color: transparent;
}

.chat-message.user .avatar-glyph { color: var(--text-user-bubble); opacity: 0.85; }

/* 消息头 */
.msg-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 5px;
}

.msg-author {
  font-family: var(--font-serif);
  font-size: 0.8125rem;
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.msg-head-time {
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* 气泡：助手用"纸页"质感（浅底 + 发丝线），用户用墨块 */
.bubble {
  border-radius: var(--radius);
  padding: 12px 14px;
}

.chat-message.ai .bubble {
  background: var(--bg-step);
  border: 1px solid var(--rule);
  color: var(--text-primary);
  border-top-left-radius: 3px;
}

.chat-message.user .bubble {
  background: var(--bg-bubble-user);
  border: 1px solid transparent;
  color: var(--text-user-bubble);
  border-top-right-radius: 3px;
}

.bubble-text {
  line-height: 1.8;
  font-size: 0.9rem;
}

.bubble-text :deep(p) + :deep(p) { margin-top: 0.65em; }

/* 流式光标：方块闪烁，不用光晕 */
.streaming-cursor::after {
  content: "";
  display: inline-block;
  width: 6px;
  height: 0.95em;
  margin-left: 2px;
  vertical-align: -0.12em;
  background: var(--accent);
  border-radius: 1px;
  animation: caret-blink 1s steps(1) infinite;
}

/* 意图标签：安静的小 chip */
.intent-tag {
  background: transparent;
  border: 1px solid var(--border-secondary);
  color: var(--text-tertiary);
  font-size: 0.6875rem;
  letter-spacing: 0.02em;
  padding: 2px 7px;
  border-radius: var(--radius-full);
}

.intent-confidence {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  opacity: 0.75;
}

/* 转人工：黄铜色提示，像一张便签 */
.handoff-alert {
  background: var(--accent-love-bg);
  border: 1px solid var(--accent-love-border);
  color: var(--accent-love);
  border-radius: var(--radius-sm);
}

/* 澄清卡片：文件式分组 */
.clarification-card {
  background: transparent;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  padding: 12px;
}

.clarification-title {
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}

.clarification-option {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-secondary);
  transition: all var(--duration-fast) var(--ease-out);
}

.clarification-option:hover {
  border-color: var(--accent-border);
  color: var(--text-primary);
  background: var(--bg-hover);
}

/* 出处：脚注风格 */
.source-citations {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--rule);
  color: var(--text-tertiary);
  font-size: 0.6875rem;
  gap: 6px;
}

.source-tag {
  border: 1px solid var(--border-secondary);
  background: transparent;
  color: var(--text-tertiary);
  border-radius: var(--radius-xs);
  padding: 1px 6px;
  font-size: 0.6875rem;
}

/* 处理过程：像一段运行日志 */
.thinking-block {
  border: 1px solid var(--rule);
  background: transparent;
  border-radius: var(--radius-sm);
  margin-bottom: 10px;
  overflow: hidden;
}

.thinking-header {
  padding: 8px 10px;
  color: var(--text-tertiary);
  font-size: 0.75rem;
  letter-spacing: 0.03em;
}

.thinking-header:hover { background: var(--bg-hover); color: var(--text-secondary); }

.thinking-title { font-weight: var(--weight-medium); }

.thinking-body-inner { padding: 4px 12px 10px; }

.thinking-step {
  position: relative;
  padding-left: 14px;
  border-left: 1px solid var(--rule);
  margin-left: 4px;
}

.step-label-text {
  font-family: var(--font-mono);
  font-size: 0.625rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}

.step-text {
  font-size: 0.75rem;
  color: var(--text-secondary);
  line-height: 1.7;
}

.step-icon { opacity: 0.65; font-size: 0.75rem; }

/* 打字指示器 */
.typing-indicator span {
  background: var(--text-tertiary);
  animation: dot-breathe 1.2s var(--ease-in-out) infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.15s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.3s; }

/* 推荐追问：行式，不是按钮堆 */
.suggested-questions {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--rule);
  gap: 2px;
}

.suggested-label {
  font-size: 0.625rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}

.suggested-item {
  text-align: left;
  padding: 6px 8px;
  border-radius: var(--radius-xs);
  color: var(--text-secondary);
  font-size: 0.8125rem;
  transition: background var(--duration-fast) var(--ease-out),
              color var(--duration-fast) var(--ease-out);
}

.suggested-item:hover { background: var(--bg-hover); color: var(--text-primary); }

.msg-time {
  font-size: 0.625rem;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
  margin-top: 4px;
}
</style>
