import { ref, computed } from 'vue'
import { useAuth } from '../api/auth.js'

const STORAGE_KEY = 'ai-agent-conversations'

// Module-level singletons
const conversations = ref([])
const activeConversationId = ref(null)
const activeAgent = ref('customer_service')
let saveTimer = null

/**
 * Generate a unique ID
 */
function generateId(prefix = 'conv') {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`
}

/**
 * Format time as HH:mm
 */
function formatTime(timestamp) {
  const d = new Date(timestamp)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/**
 * Get calendar date string (YYYY-MM-DD)
 */
function toDateStr(ts) {
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function useConversations() {
  const { isAuthenticated, getHeaders } = useAuth()
  const API_BASE = '/api'

  // ---- Backend API helpers ----

  async function apiFetch(path, options = {}) {
    const headers = { ...getHeaders(), ...(options.headers || {}) }
    const res = await fetch(`${API_BASE}${path}`, { ...options, headers })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new Error(text || `API error ${res.status}`)
    }
    return res.json()
  }

  async function loadFromServer() {
    try {
      const serverConvs = await apiFetch('/conversations')
      // Each server conversation needs its messages loaded
      const results = []
      for (const sc of serverConvs) {
        try {
          const detail = await apiFetch(`/conversations/${sc.id}`)
          const msgs = (detail.messages || []).map(m => ({
            id: `${m.id}`,
            content: m.content || '',
            isUser: m.role === 'user',
            loading: false,
            stepLabel: '',
            thinkingSteps: [],
            imageUrl: m.imageUrl || null,
            time: m.createdAt ? formatTime(new Date(m.createdAt).getTime()) : '',
            timestamp: m.createdAt ? new Date(m.createdAt).getTime() : Date.now()
          }))
          results.push({
            id: sc.id,
            agent: sc.agentType || 'love',
            title: sc.title || '',
            messages: msgs,
            createdAt: sc.createdAt ? new Date(sc.createdAt).getTime() : Date.now(),
            updatedAt: sc.updatedAt ? new Date(sc.updatedAt).getTime() : Date.now(),
            _serverId: true
          })
        } catch {
          // Skip conversations that fail to load
          results.push({
            id: sc.id,
            agent: sc.agentType || 'love',
            title: sc.title || '',
            messages: [],
            createdAt: sc.createdAt ? new Date(sc.createdAt).getTime() : Date.now(),
            updatedAt: sc.updatedAt ? new Date(sc.updatedAt).getTime() : Date.now(),
            _serverId: true
          })
        }
      }
      return results
    } catch (e) {
      console.warn('Failed to load conversations from server:', e)
      return null
    }
  }

  // ---- Persistence ----

  async function loadConversations() {
    if (isAuthenticated.value) {
      const serverData = await loadFromServer()
      if (serverData) {
        conversations.value = serverData
        if (conversations.value.length > 0) {
          const sorted = [...conversations.value].sort((a, b) => b.updatedAt - a.updatedAt)
          activeConversationId.value = sorted[0].id
          activeAgent.value = sorted[0].agent
        }
        return
      }
    }

    // Fallback to localStorage
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) {
          conversations.value = parsed
        }
      }
    } catch (e) {
      console.warn('Failed to load conversations:', e)
      conversations.value = []
    }

    if (conversations.value.length > 0) {
      const sorted = [...conversations.value].sort((a, b) => b.updatedAt - a.updatedAt)
      activeConversationId.value = sorted[0].id
      activeAgent.value = sorted[0].agent
    }
  }

  function saveConversations() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => {
      // Always save to localStorage as fallback
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations.value))
      } catch (e) {
        console.warn('Failed to save conversations:', e)
      }
    }, 200)
  }

  // ---- Conversation CRUD ----

  function createConversation(agent) {
    const conv = {
      id: generateId('conv'),
      agent: agent || activeAgent.value,
      title: '',
      messages: [],
      createdAt: Date.now(),
      updatedAt: Date.now()
    }
    conversations.value.unshift(conv)
    activeConversationId.value = conv.id
    activeAgent.value = conv.agent
    saveConversations()

    // Persist to server
    if (isAuthenticated.value) {
      apiFetch('/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: conv.id,
          title: conv.title,
          agentType: conv.agent
        })
      }).catch(e => console.warn('Failed to create conversation on server:', e))
    }

    return conv
  }

  function deleteConversation(id) {
    const idx = conversations.value.findIndex(c => c.id === id)
    if (idx === -1) return
    conversations.value.splice(idx, 1)
    saveConversations()

    // Delete from server
    if (isAuthenticated.value) {
      apiFetch(`/conversations/${id}`, { method: 'DELETE' })
        .catch(e => console.warn('Failed to delete conversation on server:', e))
    }

    // If deleted the active conversation, switch to the next one or create new
    if (activeConversationId.value === id) {
      if (conversations.value.filter(c => c.agent === activeAgent.value).length > 0) {
        const sameAgent = conversations.value.filter(c => c.agent === activeAgent.value)
        activeConversationId.value = sameAgent[0].id
      } else {
        createConversation(activeAgent.value)
      }
    }
  }

  // ---- Active conversation ----

  const activeConversation = computed(() => {
    return conversations.value.find(c => c.id === activeConversationId.value) || null
  })

  function setActiveConversation(id) {
    const conv = conversations.value.find(c => c.id === id)
    if (conv) {
      activeConversationId.value = id
      activeAgent.value = conv.agent
    }
  }

  function setActiveAgent(agent) {
    if (activeAgent.value === agent) return
    activeAgent.value = agent
    // Switch to the most recent conversation of this agent, or create new
    const agentConvs = conversations.value.filter(c => c.agent === agent)
    if (agentConvs.length > 0) {
      activeConversationId.value = agentConvs[0].id
    } else {
      createConversation(agent)
    }
  }

  // ---- Messages ----

  function addMessage(convId, message) {
    const conv = conversations.value.find(c => c.id === convId)
    if (!conv) return null

    const msg = {
      id: generateId('msg'),
      content: message.content || '',
      isUser: message.isUser ?? false,
      loading: message.loading ?? false,
      stepLabel: message.stepLabel || '',
      thinkingSteps: message.thinkingSteps || [],
      imageUrl: message.imageUrl || null,
      time: message.time || formatTime(Date.now()),
      timestamp: Date.now()
    }
    conv.messages.push(msg)
    conv.updatedAt = Date.now()

    // Set title from first user message
    if (!conv.title && msg.isUser && msg.content) {
      conv.title = msg.content.length > 30 ? msg.content.substring(0, 30) + '...' : msg.content
      // Update title on server
      if (isAuthenticated.value) {
        apiFetch(`/conversations/${convId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ title: conv.title })
        }).catch(() => {})
      }
    }

    saveConversations()

    // Persist message to server (skip loading placeholders)
    if (isAuthenticated.value && !msg.loading && msg.content) {
      apiFetch(`/conversations/${convId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: msg.isUser ? 'user' : 'assistant',
          content: msg.content
        })
      }).catch(e => console.warn('Failed to persist message:', e))
    }

    return msg
  }

  function updateLastMessage(convId, updates) {
    const conv = conversations.value.find(c => c.id === convId)
    if (!conv || conv.messages.length === 0) return

    const lastMsg = conv.messages[conv.messages.length - 1]
    Object.assign(lastMsg, updates)

    conv.updatedAt = Date.now()
    saveConversations()

    // Persist the completed AI message to server
    if (isAuthenticated.value && updates.loading === false && !lastMsg.isUser && lastMsg.content) {
      apiFetch(`/conversations/${convId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: 'assistant',
          content: lastMsg.content
        })
      }).catch(() => {})
    }
  }

  function getLastMessage(convId) {
    const conv = conversations.value.find(c => c.id === convId)
    if (!conv || conv.messages.length === 0) return null
    return conv.messages[conv.messages.length - 1]
  }

  // ---- Grouping ----

  function getGroupedConversations(agent) {
    const now = new Date()
    const today = toDateStr(now.getTime())
    const yesterday = toDateStr(now.getTime() - 86400000)
    const weekAgo = now.getTime() - 7 * 86400000

    const filtered = conversations.value
      .filter(c => c.agent === agent)
      .sort((a, b) => b.updatedAt - a.updatedAt)

    const groups = [
      { label: '今天', items: [] },
      { label: '昨天', items: [] },
      { label: '近七天', items: [] },
      { label: '更早', items: [] }
    ]

    for (const conv of filtered) {
      const dateStr = toDateStr(conv.updatedAt)
      if (dateStr === today) {
        groups[0].items.push(conv)
      } else if (dateStr === yesterday) {
        groups[1].items.push(conv)
      } else if (conv.updatedAt >= weekAgo) {
        groups[2].items.push(conv)
      } else {
        groups[3].items.push(conv)
      }
    }

    return groups.filter(g => g.items.length > 0)
  }

  return {
    // State
    conversations,
    activeConversationId,
    activeAgent,
    activeConversation,

    // Conversation CRUD
    loadConversations,
    createConversation,
    deleteConversation,
    setActiveConversation,
    setActiveAgent,

    // Messages
    addMessage,
    updateLastMessage,
    getLastMessage,

    // Grouping
    getGroupedConversations
  }
}
