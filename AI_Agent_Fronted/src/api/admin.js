import axios from 'axios'

const API_BASE = '/api'

function getAuthHeaders() {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function uploadFaqFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await axios.post(`${API_BASE}/admin/faq/upload`, formData, {
    headers: { ...getAuthHeaders(), 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export async function getFaqList() {
  const res = await axios.get(`${API_BASE}/admin/faq/list`, { headers: getAuthHeaders() })
  return res.data
}

export async function deleteFaqFile(filename) {
  const res = await axios.delete(`${API_BASE}/admin/faq/${encodeURIComponent(filename)}`, {
    headers: getAuthHeaders()
  })
  return res.data
}

export async function reloadKnowledge() {
  const res = await axios.post(`${API_BASE}/admin/faq/reload`, null, { headers: getAuthHeaders() })
  return res.data
}
