/**
 * 最小验证脚本：
 *   node scripts/test-http.mjs
 * 覆盖 Authorization 拼接、GET 参数、POST JSON、401 两次校验、403 与网络异常保留 token。
 */
import assert from 'node:assert/strict'

const storage = new Map()
globalThis.localStorage = {
  getItem: key => storage.has(key) ? storage.get(key) : null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: key => storage.delete(key)
}
globalThis.window = {
  location: { pathname: '/analytics', search: '?tab=1', hash: '', href: '' }
}

let mode = 'ok'
let analyticsCalls = 0
let meCalls = 0
const calls = []

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

globalThis.fetch = async (url, options = {}) => {
  calls.push({ url: String(url), options })
  const target = String(url)

  if (mode === 'network') throw new TypeError('network down')
  if (mode === 'forbidden') return jsonResponse({ error: 'forbidden' }, 403)
  if (mode === 'expired') {
    if (target.includes('/auth/me')) {
      meCalls += 1
      return jsonResponse({ error: 'expired' }, 401)
    }
    return jsonResponse({ error: 'expired' }, 401)
  }
  if (mode === 'recover') {
    if (target.includes('/auth/me')) {
      meCalls += 1
      return jsonResponse({ username: 'admin', role: 'admin' }, 200)
    }
    analyticsCalls += 1
    return analyticsCalls === 1
      ? jsonResponse({ error: 'expired' }, 401)
      : jsonResponse({ totalConversations: 7 }, 200)
  }
  return jsonResponse({ ok: true, totalConversations: 7 })
}

const http = await import('../src/api/http.js')

// 1. header / params / GET
http.setToken('token-abc')
assert.deepEqual(http.authHeaders({ 'X-Test': '1' }), {
  Authorization: 'Bearer token-abc',
  'X-Test': '1'
})
const overview = await http.get('/analytics/overview', { page: 1, empty: '' })
assert.deepEqual(overview, { ok: true, totalConversations: 7 })
assert.equal(calls.at(-1).url, '/api/analytics/overview?page=1')
assert.equal(calls.at(-1).options.headers.Authorization, 'Bearer token-abc')
assert.equal(calls.at(-1).options.method, 'GET')

// 2. POST 自动 JSON 序列化
await http.post('/demo', { hello: 'world' })
assert.equal(calls.at(-1).options.method, 'POST')
assert.equal(calls.at(-1).options.headers['Content-Type'], 'application/json')
assert.equal(calls.at(-1).options.body, '{"hello":"world"}')

// 3. 403：不清 token、不校验 /auth/me
mode = 'forbidden'
meCalls = 0
await assert.rejects(http.get('/admin/eval/history'), err => {
  assert.equal(err.status, 403)
  assert.equal(err.message, '无权限（需要管理员）')
  return true
})
assert.equal(http.getToken(), 'token-abc')
assert.equal(meCalls, 0)

// 4. 401 -> /auth/me 也 401：清 token 并跳登录页
mode = 'expired'
meCalls = 0
window.location.href = ''
await assert.rejects(http.get('/analytics/overview'), err => err.status === 401)
assert.equal(meCalls, 1)
assert.equal(http.getToken(), '')
assert.equal(window.location.href, '/login?redirect=%2Fanalytics%3Ftab%3D1')

// 5. 401 -> /auth/me 200：保留 token 并重试原请求
http.setToken('token-xyz')
mode = 'recover'
meCalls = 0
analyticsCalls = 0
window.location.href = ''
const recovered = await http.get('/analytics/overview')
assert.deepEqual(recovered, { totalConversations: 7 })
assert.equal(meCalls, 1)
assert.equal(analyticsCalls, 2)
assert.equal(http.getToken(), 'token-xyz')
assert.equal(window.location.href, '')

// 6. 网络失败：保留 token
mode = 'network'
await assert.rejects(http.get('/analytics/overview'), err => err.name === 'NetworkError')
assert.equal(http.getToken(), 'token-xyz')

console.log('HTTP 自测通过：header / params / JSON / 401 / 403 / network')
