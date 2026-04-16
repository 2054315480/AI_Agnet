<template>
  <div class="chat-view">
    <!-- Mobile FAB toggle -->
    <button class="mobile-fab" @click="sidebarStore.toggleSidebar()">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="3" y1="12" x2="21" y2="12"/>
        <line x1="3" y1="6" x2="21" y2="6"/>
        <line x1="3" y1="18" x2="21" y2="18"/>
      </svg>
    </button>

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
          :time="msg.time"
          :agent="activeAgent"
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
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, inject } from 'vue'
import { useConversations } from '../composables/useConversations.js'
import { useChat } from '../composables/useChat.js'
import WelcomeScreen from './WelcomeScreen.vue'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'

const { activeConversation, activeAgent } = useConversations()
const { isLoading, sendMessage, stopGeneration } = useChat()

// Injected from App.vue for mobile FAB
const sidebarStore = inject('sidebarStore', { toggleSidebar: () => {} })

const chatInputRef = ref(null)
const messagesAreaRef = ref(null)

const inputPlaceholder = computed(() =>
  activeAgent.value === 'love'
    ? '输入你想问的恋爱问题...'
    : '输入你的任务需求...'
)

function handleSend(text) {
  sendMessage(text)
}

// Auto-scroll logic
function scrollToBottom(force = false) {
  nextTick(() => {
    const el = messagesAreaRef.value
    if (!el) return
    if (force) {
      el.scrollTop = el.scrollHeight
      return
    }
    // Only auto-scroll if user is near the bottom (within 80px)
    const threshold = 80
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    if (distanceFromBottom < threshold) {
      el.scrollTop = el.scrollHeight
    }
  })
}

// Watch for new messages
watch(() => activeConversation.value?.messages?.length, () => {
  scrollToBottom()
})

// Watch for content updates (streaming)
watch(
  () => {
    const conv = activeConversation.value
    if (!conv || conv.messages.length === 0) return ''
    const last = conv.messages[conv.messages.length - 1]
    return last ? last.content : ''
  },
  () => {
    // Force scroll during streaming if already near bottom
    scrollToBottom()
  }
)

// Scroll on first message
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
  height: 100vh;
  background: var(--bg-chat);
  overflow: hidden;
  position: relative;
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
  z-index: 30;
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
</style>
