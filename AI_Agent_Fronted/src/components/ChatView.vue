<template>
  <div class="chat-view" :class="{ 'drawer-open': drawerOpen }">
    <!-- 顶部加载进度线：只保留一条 1px 的细线，不做遮罩 -->
    <div class="top-progress" :class="{ active: isLoading }" aria-hidden="true"></div>

    <!-- Mobile FAB toggle -->
    <button class="mobile-fab" aria-label="打开菜单" @click="sidebarStore.toggleSidebar()">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="3" y1="12" x2="21" y2="12"/>
        <line x1="3" y1="6" x2="21" y2="6"/>
        <line x1="3" y1="18" x2="21" y2="18"/>
      </svg>
    </button>

    <!-- 顶部工具条：会话标题 + 会话信息入口 -->
    <header class="chat-topbar">
      <div class="topbar-left">
        <span class="topbar-title" :title="conversationTitle">{{ conversationTitle }}</span>
        <span v-if="messageCount" class="topbar-meta">{{ messageCount }} 条消息</span>
      </div>
      <button
        class="topbar-btn"
        :class="{ active: drawerOpen }"
        aria-label="打开会话信息"
        @click="drawerOpen = !drawerOpen"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="9"/>
          <line x1="12" y1="11" x2="12" y2="16"/>
          <line x1="12" y1="8" x2="12.01" y2="8"/>
        </svg>
        <span>会话信息</span>
      </button>
    </header>

    <!-- Welcome state -->
    <WelcomeScreen
      v-if="!activeConversation || activeConversation.messages.length === 0"
      :agent="activeAgent"
      @quick-send="handleSend"
    />

    <!-- Messages area -->
    <div v-else class="messages-area" ref="messagesAreaRef">
      <div class="messages-inner">
        <ChatMessage
          v-for="msg in activeConversation.messages"
          :key="msg.id"
          :content="msg.content"
          :is-user="msg.isUser"
          :loading="msg.loading"
          :step-label="msg.stepLabel"
          :thinking-steps="msg.thinkingSteps || []"
          :image-url="msg.imageUrl"
          :time="msg.time"
          :agent="activeAgent"
          :intent-info="msg.intentInfo || null"
          :is-clarification="msg.isClarification || false"
          :is-handoff="msg.isHandoff || false"
          :suggested-questions="msg.suggestedQuestions || []"
          :clarification-options="msg.clarificationOptions || []"
          :sources="msg.sources || []"
          @ask-question="handleSend"
        />
      </div>
    </div>

    <!-- Input area -->
    <div class="input-area">
      <ChatInput
        ref="chatInputRef"
        :agent="activeAgent"
        :placeholder="inputPlaceholder"
        :disabled="isLoading"
        @send="handleSend"
      />
    </div>

    <!-- 右侧会话信息抽屉 -->
    <ChatInfoDrawer
      :open="drawerOpen"
      :conversation="activeConversation"
      @close="drawerOpen = false"
      @ended="onSessionEnded"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, inject } from 'vue'
import { useConversations } from '../composables/useConversations.js'
import { useChat } from '../composables/useChat.js'
import WelcomeScreen from './WelcomeScreen.vue'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'
import ChatInfoDrawer from './ChatInfoDrawer.vue'

const { activeConversation, activeAgent } = useConversations()
const { isLoading, sendMessage, stopGeneration } = useChat()

// Injected from App.vue for mobile FAB
const sidebarStore = inject('sidebarStore', { toggleSidebar: () => {} })

const chatInputRef = ref(null)
const messagesAreaRef = ref(null)
// 支持 ?panel=info 直接打开会话信息抽屉（便于分享/深链）
const drawerOpen = ref(new URLSearchParams(window.location.search).get('panel') === 'info')

const conversationTitle = computed(() => activeConversation.value?.title || '新对话')
const messageCount = computed(() => activeConversation.value?.messages?.length || 0)

function onSessionEnded() {
  // 结束会话后收起抽屉，下一条提问会自动开启新会话
  setTimeout(() => { drawerOpen.value = false }, 1200)
}

// 目前只提供智能客服一个入口，占位文案不再按 agent 分支（历史 love 会话也能正常使用）
const inputPlaceholder = computed(() => '请描述您的问题，例如"查询订单"或"退款政策"...')

function handleSend(text, imageFile, imageUrl) {
  sendMessage(text, imageFile, imageUrl)
  // Force scroll to bottom after sending a message
  scrollToBottom(true)
}

// Auto-scroll — uses requestAnimationFrame after nextTick for reliable DOM timing
function scrollToBottom(force = false) {
  nextTick(() => {
    requestAnimationFrame(() => {
      const el = messagesAreaRef.value
      if (!el) return

      if (force) {
        el.scrollTop = el.scrollHeight
        return
      }

      // During streaming (any message is loading), always force scroll
      const conv = activeConversation.value
      const lastMsg = conv?.messages?.[conv.messages.length - 1]
      if (lastMsg && lastMsg.loading) {
        el.scrollTop = el.scrollHeight
        return
      }

      // Otherwise only scroll if user is near bottom (within 120px)
      const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
      if (distanceFromBottom < 120) {
        el.scrollTop = el.scrollHeight
      }
    })
  })
}

// Watch for new messages (count changes)
watch(() => activeConversation.value?.messages?.length, () => {
  scrollToBottom()
})

// Watch for content updates (streaming: content grows on the same message)
watch(
  () => {
    const conv = activeConversation.value
    if (!conv || conv.messages.length === 0) return ''
    const last = conv.messages[conv.messages.length - 1]
    return last ? last.content : ''
  },
  () => {
    scrollToBottom()
  }
)

// Watch for thinkingSteps updates (Manus streaming — steps grow on same message)
watch(
  () => {
    const conv = activeConversation.value
    if (!conv || conv.messages.length === 0) return 0
    const last = conv.messages[conv.messages.length - 1]
    return last?.thinkingSteps?.length || 0
  },
  () => {
    scrollToBottom()
  }
)

// Scroll when switching conversations
watch(() => activeConversation.value?.id, () => {
  scrollToBottom(true)
  nextTick(() => chatInputRef.value?.focus())
})

onMounted(() => {
  chatInputRef.value?.focus()
  scrollToBottom(true)
})
</script>

<style scoped>
.chat-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: var(--bg-chat);
  overflow: hidden;
  position: relative;
}

/* 宽屏下抽屉是常驻侧栏：给它让出 340px，避免遮住对话 */
@media (min-width: 1100px) {
  .chat-view {
    transition: padding-right 260ms var(--ease-out);
  }
  .chat-view.drawer-open {
    padding-right: 340px;
  }
}

/* ===== 顶部工具条 ===== */
.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 48px;
  flex-shrink: 0;
  padding: 0 18px;
  border-bottom: 1px solid var(--rule);
  background: var(--bg-chat);
  z-index: 6;
}

.topbar-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.topbar-title {
  font-family: var(--font-serif);
  font-size: 0.9375rem;
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 52vw;
}

.topbar-meta {
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.topbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--text-secondary);
  transition: all var(--duration-fast) var(--ease-out);
}

.topbar-btn:hover,
.topbar-btn.active {
  border-color: var(--accent-border);
  color: var(--text-primary);
  background: var(--bg-hover);
}

/* ===== Mobile FAB ===== */
.mobile-fab {
  display: none;
  position: absolute;
  top: 12px;
  left: 12px;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--bg-sidebar);
  border: 1px solid var(--border-primary);
  color: var(--text-secondary);
  align-items: center;
  justify-content: center;
  z-index: var(--z-fab);
  box-shadow: var(--shadow-md);
  transition: var(--transition);
}

.mobile-fab:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

/* ===== Messages area ===== */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 0;
}

.messages-inner {
  max-width: var(--chat-max-width);
  margin: 0 auto;
  padding: 0 24px;
}

/* ===== Input area ===== */
.input-area {
  flex-shrink: 0;
  padding: 0 24px 20px;
  max-width: calc(var(--chat-max-width) + 48px);
  margin: 0 auto;
  width: 100%;
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .chat-topbar {
    padding-left: 60px;
  }

  .topbar-title {
    max-width: 42vw;
  }

  .mobile-fab {
    display: flex;
  }

  .messages-inner {
    padding: 0 12px;
  }

  .input-area {
    padding: 0 12px 12px;
  }
}

@media (max-width: 480px) {
  .messages-area {
    padding: 12px 0;
  }

  .input-area {
    padding: 0 8px 10px;
  }
}

/* ===== 顶部加载细线 ===== */
.top-progress {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  background-size: 40% 100%;
  background-repeat: no-repeat;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
  z-index: 5;
  pointer-events: none;
}

.top-progress.active {
  opacity: 1;
  animation: progress-slide 1.4s var(--ease-in-out) infinite;
}

@keyframes progress-slide {
  0%   { background-position: -40% 0; }
  100% { background-position: 140% 0; }
}
</style>
