import { request, get, post } from './http.js'

export function uploadFaqFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request('/admin/faq/upload', { method: 'POST', body: formData })
}

export function getFaqList() {
  return get('/admin/faq/list')
}

export function deleteFaqFile(filename) {
  return request(`/admin/faq/${encodeURIComponent(filename)}`, { method: 'DELETE' })
}

export function reloadKnowledge() {
  return post('/admin/faq/reload')
}
