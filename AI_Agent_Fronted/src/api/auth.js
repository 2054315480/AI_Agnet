import { ref } from 'vue'

const API_BASE = '/api'

const user = ref(null)
const token = ref(localStorage.getItem('token') || '')
const isAuthenticated = ref(!!token.value)

function getHeaders() {
  const headers = { 'Content-Type': 'application/json' }
  if (token.value) {
    headers['Authorization'] = `Bearer ${token.value}`
  }
  return headers
}

async function register(username, password) {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.error || '注册失败')
  token.value = data.token
  user.value = data.user
  isAuthenticated.value = true
  localStorage.setItem('token', data.token)
  return data
}

async function login(username, password) {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const data = await res.json()
  if (!res.ok) {
    console.error(`[Auth] 登录失败: ${res.status}`, data)
    throw new Error(data.error || '登录失败')
  }
  token.value = data.token
  user.value = data.user
  isAuthenticated.value = true
  localStorage.setItem('token', data.token)
  console.log('[Auth] 登录成功:', username)
  return data
}

function logout() {
  token.value = ''
  user.value = null
  isAuthenticated.value = false
  localStorage.removeItem('token')
}

async function fetchUser() {
  if (!token.value) return
  try {
    const res = await fetch(`${API_BASE}/auth/me`, { headers: getHeaders() })
    if (res.ok) {
      user.value = await res.json()
      isAuthenticated.value = true
    } else {
      console.warn(`[Auth] /auth/me 返回 ${res.status}，清除旧 token`)
      logout()
    }
  } catch (e) {
    console.warn('[Auth] fetchUser 异常:', e.message)
    logout()
  }
}

export function useAuth() {
  return { user, token, isAuthenticated, register, login, logout, fetchUser, getHeaders }
}
