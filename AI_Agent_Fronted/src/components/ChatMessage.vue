<template>
  <div class="chat-message" :class="[isUser ? 'user' : 'ai', `agent-${agent}`]">
    <!-- Avatar -->
    <div class="avatar" :class="{ 'user-avatar': isUser }">
      <template v-if="isUser">
        <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="20" cy="20" r="20" fill="var(--accent-primary)" opacity="0.15"/>
          <circle cx="20" cy="15" r="6" fill="var(--accent-primary)"/>
          <path d="M8 34c0-6.627 5.373-12 12-12s12 5.373 12 12" fill="var(--accent-primary)"/>
        </svg>
      </template>
      <template v-else-if="agent === 'love'">
        <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="20" cy="20" r="20" fill="var(--accent-love-bg)"/>
          <path d="M20 30l-1.5-1.3C12 23.5 8 19.8 8 15.5 8 12.4 10.4 10 13.5 10c1.8 0 3.5.8 4.5 2.2L20 14.5l2-2.3c1-1.4 2.7-2.2 4.5-2.2C29.6 10 32 12.4 32 15.5c0 4.3-4 8-10.5 13.2L20 30z" fill="var(--accent-love)"/>
        </svg>
      </template>
      <template v-else>
        <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="40" height="40" rx="10" fill="var(--accent-manus-bg)"/>
          <path d="M12 16l4 4-4 4" stroke="var(--accent-manus)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <line x1="19" y1="24" x2="28" y2="24" stroke="var(--accent-manus)" stroke-width="2.5" stroke-linecap="round"/>
        </svg>
      </template>
    </div>
    <!-- Bubble -->
    <div class="bubble-wrap">
      <!-- Step block for Manus steps -->
      <StepBlock
        v-if="stepLabel && !isUser && agent === 'manus'"
        :label="stepLabel"
        :content="content"
        :defaultOpen="true"
      />
      <!-- Regular bubble (shown when no stepLabel, or for love agent) -->
      <template v-else>
        <div class="bubble" :class="{ 'is-loading': loading && !content }">
          <div
            class="bubble-text"
            :class="{ 'streaming-cursor': loading && content }"
            v-html="renderedContent"
          ></div>
          <div v-if="loading && !content" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </template>
      <div v-if="time" class="msg-time">{{ time }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import StepBlock from './StepBlock.vue'

marked.setOptions({
  breaks: true,
  gfm: true
})

const props = defineProps({
  content: { type: String, default: '' },
  isUser: { type: Boolean, default: false },
  agent: { type: String, default: 'love' },
  loading: { type: Boolean, default: false },
  stepLabel: { type: String, default: '' },
  time: { type: String, default: '' }
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
  animation: fadeIn 0.3s ease;
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
  margin: 6px 0;
  line-height: 1.7;
}

.bubble-text :deep(p:first-child) {
  margin-top: 0;
}

.bubble-text :deep(p:last-child) {
  margin-bottom: 0;
}

.bubble-text :deep(ul),
.bubble-text :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}

.bubble-text :deep(li) {
  margin: 3px 0;
  line-height: 1.6;
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
</style>
