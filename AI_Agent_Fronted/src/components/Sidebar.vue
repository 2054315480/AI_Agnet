<template>
  <aside class="sidebar" :class="{ collapsed: !open }">
    <!-- Top: toggle + brand -->
    <div class="sidebar-top">
      <button class="sidebar-toggle" @click="$emit('toggle')">
        <svg v-if="open" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="3" y1="12" x2="21" y2="12"/>
          <line x1="3" y1="6" x2="21" y2="6"/>
          <line x1="3" y1="18" x2="21" y2="18"/>
        </svg>
        <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="9" y1="3" x2="9" y2="21"/>
        </svg>
      </button>
      <div v-if="open" class="sidebar-brand">
        <svg width="28" height="28" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="40" height="40" rx="10" fill="var(--accent-primary)" />
          <text x="20" y="26" text-anchor="middle" fill="#fff" font-size="16" font-weight="700" font-family="system-ui">AI</text>
        </svg>
        <span class="brand-text">AI Agent</span>
      </div>
    </div>

    <template v-if="open">
      <!-- New chat button -->
      <div class="sidebar-section">
        <button class="new-chat-btn" @click="handleNewChat">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          <span>新建对话</span>
        </button>
      </div>

      <!-- Agent tabs -->
      <div class="sidebar-section">
        <div class="agent-tabs">
          <button
            class="agent-tab"
            :class="{ active: activeAgent === 'love' }"
            @click="switchAgent('love')"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
            <span>恋爱大师</span>
          </button>
          <button
            class="agent-tab"
            :class="{ active: activeAgent === 'manus' }"
            @click="switchAgent('manus')"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="4 17 10 11 4 5"/>
              <line x1="12" y1="19" x2="20" y2="19"/>
            </svg>
            <span>超级智能体</span>
          </button>
        </div>
      </div>

      <!-- Conversation history -->
      <div class="sidebar-section history-section">
        <div
          v-for="group in groupedConversations"
          :key="group.label"
          class="history-group"
        >
          <div class="history-group-label">{{ group.label }}</div>
          <button
            v-for="conv in group.items"
            :key="conv.id"
            class="history-item"
            :class="{ active: conv.id === activeConversationId }"
            @click="openConversation(conv.id)"
          >
            <span class="history-item-title">{{ conv.title || '新对话' }}</span>
            <button class="history-delete" @click.stop="deleteConv(conv.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </button>
        </div>
        <div v-if="groupedConversations.length === 0" class="history-empty">
          暂无对话记录
        </div>
      </div>
    </template>

    <!-- Bottom -->
    <div class="sidebar-bottom">
      <ThemeToggle v-if="open" />
      <div v-if="open && currentUser" class="user-info">
        <span class="user-name">{{ currentUser.username }}</span>
        <button class="logout-btn" @click="handleLogout">退出</button>
      </div>
      <div v-if="open" class="sidebar-footer-text">秋鹤出品 &middot; AI Agent v1.0</div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useConversations } from '../composables/useConversations.js'
import { useAuth } from '../api/auth.js'
import ThemeToggle from './ThemeToggle.vue'

defineProps({
  open: { type: Boolean, default: true }
})

defineEmits(['toggle'])

const {
  activeAgent,
  activeConversationId,
  getGroupedConversations,
  setActiveAgent,
  setActiveConversation,
  createConversation,
  deleteConversation
} = useConversations()

const { user: currentUser, logout } = useAuth()

const groupedConversations = computed(() => getGroupedConversations(activeAgent.value))

function switchAgent(agent) {
  setActiveAgent(agent)
}

function openConversation(id) {
  setActiveConversation(id)
}

function handleNewChat() {
  createConversation(activeAgent.value)
}

function deleteConv(id) {
  deleteConversation(id)
}

function handleLogout() {
  const { conversations } = useConversations()
  conversations.value = []
  logout()
  window.location.href = '/login'
}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-secondary);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  z-index: 50;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
  align-items: center;
}

/* ===== Top section ===== */
.sidebar-top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 14px 8px;
  flex-shrink: 0;
}

.sidebar.collapsed .sidebar-top {
  justify-content: center;
  padding: 16px 0 8px;
}

.sidebar-toggle {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: var(--transition);
  flex-shrink: 0;
}

.sidebar-toggle:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.brand-text {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
}

/* ===== Section wrapper ===== */
.sidebar-section {
  padding: 0 12px;
  margin-bottom: 4px;
  flex-shrink: 0;
}

/* ===== New chat button ===== */
.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
  background: transparent;
  border: 1px solid var(--border-primary);
  transition: var(--transition);
}

.new-chat-btn:hover {
  background: var(--bg-hover);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

/* ===== Agent tabs ===== */
.agent-tabs {
  display: flex;
  gap: 4px;
  background: var(--bg-hover);
  border-radius: var(--radius-sm);
  padding: 3px;
}

.agent-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: var(--radius-xs);
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-secondary);
  transition: var(--transition);
  white-space: nowrap;
}

.agent-tab:hover {
  color: var(--text-primary);
}

.agent-tab.active {
  background: var(--bg-sidebar);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.agent-tab.active svg {
  color: var(--accent-primary);
}

/* ===== History section ===== */
.history-section {
  flex: 1;
  overflow-y: auto;
  padding-top: 8px;
}

.history-group-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-tertiary);
  padding: 8px 4px 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.history-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  color: var(--text-secondary);
  transition: var(--transition);
  text-align: left;
  gap: 6px;
}

.history-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.history-item.active {
  background: var(--bg-active);
  color: var(--text-primary);
}

.history-item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.history-delete {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  opacity: 0;
  transition: var(--transition);
}

.history-item:hover .history-delete {
  opacity: 1;
}

.history-delete:hover {
  color: var(--danger);
  background: var(--danger-bg);
}

.history-empty {
  text-align: center;
  padding: 24px 0;
  font-size: 0.8125rem;
  color: var(--text-tertiary);
}

/* ===== Bottom section ===== */
.sidebar-bottom {
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--border-secondary);
  flex-shrink: 0;
}

.sidebar-footer-text {
  font-size: 0.7rem;
  color: var(--text-tertiary);
  text-align: center;
  margin-top: 8px;
}

/* ===== Responsive: Mobile ===== */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: var(--sidebar-width);
    transform: translateX(-100%);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: none;
  }

  .sidebar.collapsed {
    transform: translateX(-100%);
    width: var(--sidebar-width);
  }

  /* When open on mobile (parent removes collapsed class) */
  .sidebar:not(.collapsed) {
    transform: translateX(0);
    box-shadow: var(--shadow-sidebar);
  }
}

.user-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}
.user-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logout-btn {
  background: none;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  padding: 2px 10px;
  font-size: 0.75rem;
  cursor: pointer;
}
.logout-btn:hover {
  color: #ff6b6b;
  border-color: #ff6b6b;
}
</style>
