<template>
  <aside class="sidebar" :class="{ collapsed: !open }">
    <!-- Top: toggle + brand -->
    <div class="sidebar-top">
      <button class="sidebar-toggle" aria-label="切换侧边栏" @click="$emit('toggle')">
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
        <BrandMark :size="30" />
        <div class="brand-copy">
          <span class="brand-text">客服工作台</span>
          <span class="brand-sub">Service Desk</span>
        </div>
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
            class="agent-tab active"
            style="flex: 1;"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <span>智能客服</span>
          </button>
        </div>
      </div>

      <!-- Conversation history -->
      <div class="sidebar-section history-section">
        <div class="conv-search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="7"/>
            <line x1="16.5" y1="16.5" x2="21" y2="21"/>
          </svg>
          <input v-model="query" type="search" placeholder="搜索会话" aria-label="搜索会话" />
        </div>
        <div class="section-label">会话</div>
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
      <div v-if="open" class="section-label tools-label">工具</div>
      <button v-if="open" class="sidebar-action-btn" @click="$emit('openBatchTest')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
        <span>批量测试</span>
      </button>
      <button v-if="open && isAdmin" class="sidebar-action-btn" @click="goToConsole">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <path d="M3 9h18M9 21V9"/>
        </svg>
        <span>能力控制台</span>
      </button>
      <button v-if="open" class="sidebar-action-btn" @click="goToImpact">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="3 17 9 11 13 15 21 7"/>
          <polyline points="15 7 21 7 21 13"/>
        </svg>
        <span>优化成效</span>
      </button>
      <button v-if="open && isAdmin" class="sidebar-action-btn" @click="goToExperiments">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M9 3h6M10 3v6L5 20a1 1 0 0 0 .9 1h12.2a1 1 0 0 0 .9-1L14 9V3"/>
          <path d="M7.5 14h9"/>
        </svg>
        <span>实验中心</span>
      </button>
      <button v-if="open && isAdmin" class="sidebar-action-btn" @click="goToAnalytics">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="20" x2="18" y2="10"/>
          <line x1="12" y1="20" x2="12" y2="4"/>
          <line x1="6" y1="20" x2="6" y2="14"/>
        </svg>
        <span>对话分析</span>
      </button>
      <button v-if="open && isAdmin" class="sidebar-action-btn" @click="goToAdminFaq">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
        </svg>
        <span>FAQ 管理</span>
      </button>
      <div v-if="open && currentUser" class="user-info">
        <span class="user-name">{{ currentUser.username }}</span>
        <button class="logout-btn" @click="handleLogout">退出</button>
      </div>
      <div v-if="open" class="sidebar-footer-text">v4.2 · 知识库在线</div>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useConversations } from '../composables/useConversations.js'
import { useAuth } from '../api/auth.js'
import ThemeToggle from './ThemeToggle.vue'
import BrandMark from './BrandMark.vue'

defineProps({
  open: { type: Boolean, default: true }
})

defineEmits(['toggle', 'openBatchTest'])

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

// 只有管理员才显示"对话分析"和"FAQ 管理"（后端 /admin、/analytics 已要求 ROLE_ADMIN）
const isAdmin = computed(() => String(currentUser.value?.role || '').toLowerCase() === 'admin')

const router = useRouter()

const query = ref('')

const groupedConversations = computed(() => {
  const groups = getGroupedConversations(activeAgent.value)
  const kw = query.value.trim().toLowerCase()
  if (!kw) return groups
  return groups
    .map(g => ({ ...g, items: g.items.filter(c => (c.title || '未命名会话').toLowerCase().includes(kw)) }))
    .filter(g => g.items.length > 0)
})

function switchAgent(agent) {
  setActiveAgent(agent)
}

function goToAdminFaq() {
  router.push('/admin/faq')
}

function goToConsole() {
  router.push('/console')
}

function goToImpact() {
  router.push('/impact')
}

function goToExperiments() {
  router.push('/experiments')
}

function goToAnalytics() {
  router.push('/analytics')
}

function openConversation(id) {
  setActiveConversation(id)
  router.push('/')
}

function handleNewChat() {
  createConversation(activeAgent.value)
  router.push('/')
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
  height: 100dvh;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-secondary);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width var(--duration-normal) var(--ease-in-out);
  overflow: hidden;
  z-index: var(--z-sidebar);
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

.sidebar-action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  color: var(--text-secondary);
  transition: var(--transition);
  margin-bottom: 4px;
}

.sidebar-action-btn:hover {
  color: var(--accent-primary);
  background: var(--bg-hover);
}

.sidebar-action-btn svg {
  flex-shrink: 0;
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
    transition: transform var(--duration-normal) var(--ease-in-out);
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

/* ================================================================
   v6 覆写：案头风格
   ================================================================ */
.sidebar {
  border-right: 1px solid var(--rule);
  background: var(--bg-sidebar);
}

.sidebar-brand { align-items: center; }


.brand-copy { display: flex; flex-direction: column; gap: 1px; min-width: 0; }

.brand-text {
  font-family: var(--font-serif);
  font-size: 0.9375rem;
  font-weight: var(--weight-semibold);
  letter-spacing: 0.01em;
  color: var(--text-primary);
  white-space: nowrap;
}

.brand-sub {
  font-size: 0.5625rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}

.section-label {
  padding: 2px 10px 6px;
  font-size: 0.625rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  user-select: none;
}

.tools-label { padding-top: 10px; }

/* 会话搜索 */
.conv-search {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 2px 8px 10px;
  padding: 0 9px;
  height: 32px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  background: transparent;
  transition: border-color var(--duration-fast) var(--ease-out),
              background var(--duration-fast) var(--ease-out);
}

.conv-search:focus-within {
  border-color: var(--accent-border);
  background: var(--bg-hover);
  color: var(--text-secondary);
}

.conv-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 0.8125rem;
}

.conv-search input::placeholder { color: var(--text-tertiary); }
.conv-search input::-webkit-search-cancel-button { -webkit-appearance: none; }

/* 新建对话：实心墨块 + 青瓷字，主操作更明确 */
.new-chat-btn {
  border: 1px solid var(--border-primary);
  background: var(--bg-step);
  color: var(--text-primary);
  font-weight: var(--weight-medium);
  transition: background var(--duration-fast) var(--ease-out),
              border-color var(--duration-fast) var(--ease-out);
}

.new-chat-btn:hover {
  background: var(--bg-active);
  border-color: var(--accent-border);
}

.new-chat-btn svg { color: var(--accent); }

/* 历史项：用左侧细线表示选中，而不是整块高亮 */
.history-item {
  position: relative;
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) var(--ease-out),
              color var(--duration-fast) var(--ease-out);
}

.history-item::before {
  content: "";
  position: absolute;
  left: 2px; top: 50%;
  width: 0; height: 14px;
  transform: translateY(-50%);
  background: var(--accent);
  border-radius: 1px;
  transition: width var(--duration-fast) var(--ease-out);
}

.history-item.active { background: var(--bg-hover); color: var(--text-primary); }
.history-item.active::before { width: 2px; }
.history-item:hover { background: var(--bg-hover); }

.history-group-label {
  font-size: 0.625rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}

/* 底部工具按钮：更克制的描边按钮 */
.sidebar-action-btn {
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  transition: background var(--duration-fast) var(--ease-out),
              color var(--duration-fast) var(--ease-out),
              border-color var(--duration-fast) var(--ease-out);
}

.sidebar-action-btn:hover {
  background: var(--bg-hover);
  border-color: var(--border-secondary);
  color: var(--text-primary);
}

.sidebar-footer-text {
  font-size: 0.625rem;
  letter-spacing: 0.08em;
  color: var(--text-tertiary);
  opacity: 0.8;
}
</style>
