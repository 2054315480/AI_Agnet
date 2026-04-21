<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <svg width="48" height="48" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="40" height="40" rx="10" fill="url(#logoGrad)" />
          <text x="20" y="26" text-anchor="middle" fill="#fff" font-size="16" font-weight="700" font-family="system-ui">AI</text>
          <defs><linearGradient id="logoGrad" x1="0" y1="0" x2="40" y2="40"><stop stop-color="#667eea"/><stop offset="1" stop-color="#764ba2"/></linearGradient></defs>
        </svg>
      </div>
      <h2>{{ isLogin ? '欢迎回来' : '创建账号' }}</h2>
      <p class="subtitle">{{ isLogin ? '登录以继续使用 AI Agent' : '注册一个新账号开始使用' }}</p>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="请输入用户名" required autocomplete="username" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码（至少6位）" required autocomplete="current-password" />
        </div>
        <button type="submit" class="submit-btn" :disabled="loading">
          <span v-if="loading" class="btn-loading">处理中...</span>
          <span v-else>{{ isLogin ? '登 录' : '注 册' }}</span>
        </button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
      <p class="switch-text">
        {{ isLogin ? '还没有账号？' : '已有账号？' }}
        <a href="#" @click.prevent="isLogin = !isLogin; error = ''">
          {{ isLogin ? '立即注册' : '去登录' }}
        </a>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../api/auth'

const router = useRouter()
const { login, register, fetchUser } = useAuth()
const isLogin = ref(true)
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    if (isLogin.value) {
      await login(username.value, password.value)
    } else {
      await register(username.value, password.value)
    }
    await fetchUser()
    router.push('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #0f0c29 0%, #1a1a2e 40%, #16213e 100%);
  position: relative;
  overflow: hidden;
}

/* Animated background orbs */
.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.3;
  pointer-events: none;
}
.login-page::before {
  width: 400px;
  height: 400px;
  background: #667eea;
  top: -100px;
  right: -100px;
}
.login-page::after {
  width: 300px;
  height: 300px;
  background: #764ba2;
  bottom: -80px;
  left: -80px;
}

.login-card {
  background: rgba(22, 33, 62, 0.85);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 44px 36px;
  width: 400px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.4);
  z-index: 1;
}

.login-logo {
  text-align: center;
  margin-bottom: 16px;
}

.login-card h2 {
  text-align: center;
  color: #f0f0f0;
  margin: 0 0 6px;
  font-size: 1.5rem;
  font-weight: 700;
}

.subtitle {
  text-align: center;
  color: rgba(255, 255, 255, 0.45);
  font-size: 0.85rem;
  margin: 0 0 28px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.8rem;
  font-weight: 500;
  margin-bottom: 6px;
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #f0f0f0;
  font-size: 14px;
  box-sizing: border-box;
  transition: all 0.25s ease;
  outline: none;
}

.form-group input::placeholder {
  color: rgba(255, 255, 255, 0.25);
}

.form-group input:focus {
  border-color: #667eea;
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.15);
}

.submit-btn {
  width: 100%;
  padding: 13px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  margin-top: 8px;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-loading {
  opacity: 0.8;
}

.error {
  color: #ff6b6b;
  font-size: 13px;
  text-align: center;
  margin-top: 14px;
  background: rgba(255, 107, 107, 0.1);
  padding: 8px 12px;
  border-radius: 8px;
}

.switch-text {
  text-align: center;
  margin-top: 24px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}

.switch-text a {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.switch-text a:hover {
  color: #8b9cf7;
}
</style>
