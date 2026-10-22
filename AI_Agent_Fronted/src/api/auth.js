import { ref } from 'vue'
import { request, getToken, setToken, clearToken, authHeaders } from './http.js'

const user = ref(null)
const token = ref(getToken())
const isAuthenticated = ref(!!token.value)

function applyToken(nextToken) {
  token.value = nextToken || ''
  isAuthenticated.value = !!token.value
  if (token.value) setToken(token.value)
  else clearToken()
}

async function register(username, password) {
  const data = await request('/auth/register', {
    method: 'POST',
    body: { username, password }
  })
  applyToken(data.token)
  user.value = data.user || null
  return data
}

async function login(username, password) {
  const data = await request('/auth/login', {
    method: 'POST',
    body: { username, password }
  })
  applyToken(data.token)
  user.value = data.user || null
  console.log('[Auth] 登录成功:', username)
  return data
}

function logout() {
  applyToken('')
  user.value = null
}

async function fetchUser() {
  token.value = getToken()
  if (!token.value) {
    isAuthenticated.value = false
    return null
  }

  try {
    const data = await request('/auth/me')
    user.value = data
    isAuthenticated.value = true
    return data
  } catch (e) {
    // 401 由 http.js 在校验 /auth/me 确实失效后统一清 token；网络抖动保留 token。
    if (e && e.status === 401) {
      token.value = ''
      user.value = null
      isAuthenticated.value = false
    } else {
      console.warn('[Auth] 获取用户信息失败，保留本地登录态:', e.message)
      isAuthenticated.value = !!getToken()
    }
    return null
  }
}

export function useAuth() {
  return {
    user,
    token,
    isAuthenticated,
    register,
    login,
    logout,
    fetchUser,
    // 兼容旧调用；新代码应直接使用 http.js 的 authHeaders/request
    getHeaders: authHeaders
  }
}
