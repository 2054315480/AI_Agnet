/**
 * 统一 HTTP 请求入口。
 * 所有业务请求都从这里发出，避免各模块重复拼接 Authorization 导致 token 丢失。
 */
const API_BASE = '/api'
const DEFAULT_TIMEOUT = 20000

let authCheckPromise = null

/** 仅用于极少数非浏览器环境的兜底；浏览器中始终以 localStorage 为准。 */
let memoryToken = ''

export function getToken() {
  try {
    if (typeof localStorage !== 'undefined') {
      return localStorage.getItem('token') || ''
    }
  } catch (e) {
    // localStorage 在隐私模式等场景可能不可用
  }
  return memoryToken
}

export function setToken(t) {
  memoryToken = t || ''
  try {
    if (typeof localStorage !== 'undefined') {
      if (memoryToken) localStorage.setItem('token', memoryToken)
      else localStorage.removeItem('token')
    }
  } catch (e) {
    // 忽略存储异常，保留内存 token
  }
}

export function clearToken() {
  memoryToken = ''
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem('token')
  } catch (e) {
    // 忽略存储异常
  }
}

/** 生成统一的鉴权头；extra 可覆盖默认值（如 multipart 场景）。 */
export function authHeaders(extra = {}) {
  const token = getToken()
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  return { ...headers, ...(extra || {}) }
}

function normalizePath(path) {
  if (!path) return API_BASE
  if (/^https?:\/\//i.test(path) || path === API_BASE || path.startsWith(API_BASE + '/')) {
    return path
  }
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
}

/** 将 params 展开为查询字符串，跳过 null / undefined / 空字符串。 */
function appendParams(url, params) {
  if (!params) return url
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') return
    if (Array.isArray(value)) {
      value.forEach(item => {
        if (item !== null && item !== undefined) search.append(key, String(item))
      })
    } else {
      search.append(key, String(value))
    }
  })
  const qs = search.toString()
  if (!qs) return url
  return `${url}${url.includes('?') ? '&' : '?'}${qs}`
}

function makeHttpError(status, data, fallbackMessage) {
  let message = fallbackMessage
  if (data && typeof data === 'object') {
    message = data.error || data.message || fallbackMessage
  } else if (typeof data === 'string' && data.trim()) {
    message = data.trim()
  }
  if (status === 403) message = '无权限（需要管理员）'
  if (status === 401 && !message) message = '登录已过期，请重新登录'

  const error = new Error(message)
  error.name = 'ApiError'
  error.status = status
  error.data = data
  // 兼容旧页面里 err.response.status 的判断
  error.response = { status, data }
  return error
}

function isAuthFreePath(url) {
  const path = String(url).split('?')[0]
  return path.endsWith('/auth/login') || path.endsWith('/auth/register')
}

function isMePath(url) {
  return String(url).split('?')[0].endsWith('/auth/me')
}

/** 401 后统一跳转登录页，并携带当前地址供登录后回跳。 */
function redirectToLogin() {
  try {
    if (typeof window === 'undefined' || !window.location) return
    const loc = window.location
    if (loc.pathname === '/login') return
    const redirect = `${loc.pathname || '/'}${loc.search || ''}${loc.hash || ''}`
    loc.href = `/login?redirect=${encodeURIComponent(redirect)}`
  } catch (e) {
    // 跳转失败不应影响错误继续向上抛出
  }
}

function expireSession() {
  clearToken()
  redirectToLogin()
}

/**
 * 校验当前 token。
 * 只有校验请求本身明确返回 401 才清 token；网络异常或服务端 5xx 均保留 token。
 */
function verifySession() {
  if (authCheckPromise) return authCheckPromise

  authCheckPromise = (async () => {
    const token = getToken()
    if (!token) {
      expireSession()
      return false
    }
    try {
      const res = await fetch(normalizePath('/auth/me'), {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` },
        credentials: 'same-origin'
      })
      if (res.status === 401) {
        // 避免并发请求期间用户已重新登录，误清新 token
        if (getToken() === token) expireSession()
        return false
      }
      return res.ok
    } catch (e) {
      // 网络抖动不能导致用户被登出
      return false
    }
  })().finally(() => {
    authCheckPromise = null
  })

  return authCheckPromise
}

async function parseResponse(response, responseType) {
  if (responseType === 'blob') return response.blob()
  if (response.status === 204) return null
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return text
  }
}

async function doRequest(url, { method, body, headers, timeout, responseType, signal } = {}, retried = false) {
  const finalHeaders = authHeaders(headers || {})
  const upperMethod = String(method || 'GET').toUpperCase()
  let payload = body

  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
  const isBlob = typeof Blob !== 'undefined' && body instanceof Blob
  const isArrayBuffer = typeof ArrayBuffer !== 'undefined' && body instanceof ArrayBuffer

  if (payload !== undefined && payload !== null && !isFormData && !isBlob && !isArrayBuffer && typeof payload === 'object') {
    payload = JSON.stringify(payload)
    if (!finalHeaders['Content-Type'] && !finalHeaders['content-type']) {
      finalHeaders['Content-Type'] = 'application/json'
    }
  } else if (typeof payload === 'string' && !finalHeaders['Content-Type'] && !finalHeaders['content-type']) {
    finalHeaders['Content-Type'] = 'application/json'
  }

  const controller = new AbortController()
  let timer = null
  const abortFromCaller = () => controller.abort()
  if (signal) {
    if (signal.aborted) controller.abort()
    else signal.addEventListener('abort', abortFromCaller, { once: true })
  }
  if (timeout !== 0) {
    timer = setTimeout(() => controller.abort(), timeout || DEFAULT_TIMEOUT)
  }

  let response
  try {
    response = await fetch(url, {
      method: upperMethod,
      headers: finalHeaders,
      body: upperMethod === 'GET' || upperMethod === 'HEAD' ? undefined : payload,
      signal: controller.signal,
      credentials: 'same-origin'
    })
  } catch (e) {
    const error = new Error(e && e.name === 'AbortError' ? '请求超时，请稍后重试' : '网络请求失败，请检查网络后重试')
    error.name = 'NetworkError'
    error.status = 0
    error.cause = e
    throw error
  } finally {
    if (timer) clearTimeout(timer)
    if (signal) signal.removeEventListener('abort', abortFromCaller)
  }

  const data = await parseResponse(response, responseType)

  if (response.status === 401) {
    // 登录/注册失败的 401 是业务校验错误，不触发全局登出逻辑
    if (isAuthFreePath(url)) {
      throw makeHttpError(401, data, '用户名或密码错误')
    }

    // /auth/me 自身返回 401，说明 token 确实失效
    if (isMePath(url)) {
      expireSession()
      throw makeHttpError(401, data, '登录已过期，请重新登录')
    }

    // 其他业务请求先校验一次会话；校验成功则用同一 token 重试一次
    if (!retried) {
      const valid = await verifySession()
      if (valid) return doRequest(url, { method, body, headers, timeout, responseType, signal }, true)
    }
    throw makeHttpError(401, data, '登录已过期，请重新登录')
  }

  if (!response.ok) {
    throw makeHttpError(response.status, data, `请求失败（HTTP ${response.status}）`)
  }

  return data
}

/**
 * 发起请求。
 * - 自动附加 Authorization
 * - 对象 body 自动 JSON 序列化
 * - 非 2xx 抛出带 status / data / response 的错误对象
 */
export async function request(path, options = {}) {
  const url = appendParams(normalizePath(path), options.params)
  return doRequest(url, options, false)
}

export function get(path, params) {
  return request(path, { method: 'GET', params })
}

export function post(path, body, options = {}) {
  return request(path, { ...options, method: 'POST', body })
}

export default { getToken, setToken, clearToken, authHeaders, request, get, post }
