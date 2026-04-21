<template>
  <div class="login-page">
    <!-- Left: Brand showcase -->
    <div class="login-brand">
      <div class="brand-bg">
        <div class="orb orb-1"></div>
        <div class="orb orb-2"></div>
        <div class="orb orb-3"></div>
        <div class="grid-lines"></div>
      </div>
      <div class="brand-content">
        <div class="brand-icon">
          <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
            <rect width="56" height="56" rx="14" fill="rgba(255,255,255,0.12)"/>
            <text x="28" y="35" text-anchor="middle" fill="#fff" font-size="22" font-weight="700" font-family="system-ui">AI</text>
          </svg>
        </div>
        <h1>AI Agent</h1>
        <p class="brand-desc">你的智能对话伙伴，随时为你提供帮助</p>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-dot love"></div>
            <span>恋爱大师 — 情感咨询与建议</span>
          </div>
          <div class="feature-item">
            <div class="feature-dot manus"></div>
            <span>超级智能体 — 复杂任务自动执行</span>
          </div>
          <div class="feature-item">
            <div class="feature-dot multi"></div>
            <span>多模态理解 — 图片识别与 PDF 解析</span>
          </div>
        </div>
      </div>
      <div class="brand-footer">秋鹤出品 &middot; AI Agent v1.0</div>
    </div>

    <!-- Right: Form -->
    <div class="login-form-side">
      <div class="form-wrapper">
        <h2>{{ isLogin ? '登录' : '注册' }}</h2>
        <p class="form-subtitle">{{ isLogin ? '欢迎回来，请输入你的账号' : '创建一个新账号开始使用' }}</p>

        <form @submit.prevent="handleSubmit">
          <div class="field">
            <label class="field-label">用户名</label>
            <div class="input-wrap">
              <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <input v-model="username" type="text" placeholder="请输入用户名" required autocomplete="username" />
            </div>
          </div>

          <div class="field">
            <label class="field-label">密码</label>
            <div class="input-wrap">
              <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input v-model="password" type="password" placeholder="请输入密码（至少6位）" required autocomplete="current-password" />
            </div>
          </div>

          <button type="submit" class="submit-btn" :disabled="loading">
            <span v-if="loading" class="spinner"></span>
            <span>{{ loading ? '处理中...' : (isLogin ? '登 录' : '注 册') }}</span>
          </button>

          <transition name="fade">
            <p v-if="error" class="error-msg">{{ error }}</p>
          </transition>
        </form>

        <div class="switch-mode">
          <span>{{ isLogin ? '还没有账号？' : '已有账号？' }}</span>
          <button class="switch-btn" @click="isLogin = !isLogin; error = ''">
            {{ isLogin ? '立即注册' : '去登录' }}
          </button>
        </div>
      </div>
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
  height: 100vh;
  overflow: hidden;
}

/* ===== Left: Brand panel ===== */
.login-brand {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  position: relative;
  background: linear-gradient(160deg, #0d1117 0%, #161b22 50%, #1a2332 100%);
  overflow: hidden;
  padding: 48px;
}

.brand-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.15;
}
.orb-1 {
  width: 500px; height: 500px;
  background: var(--accent-love);
  top: -15%; left: -10%;
  animation: float 20s ease-in-out infinite;
}
.orb-2 {
  width: 350px; height: 350px;
  background: var(--accent-manus);
  bottom: -10%; right: -5%;
  animation: float 16s ease-in-out infinite reverse;
}
.orb-3 {
  width: 200px; height: 200px;
  background: var(--accent-primary);
  top: 50%; left: 60%;
  animation: float 12s ease-in-out infinite 3s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -20px) scale(1.05); }
  66% { transform: translate(-20px, 15px) scale(0.95); }
}

.grid-lines {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px);
  background-size: 60px 60px;
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 380px;
}

.brand-icon {
  margin-bottom: 20px;
}

.brand-content h1 {
  font-size: 2rem;
  font-weight: 800;
  color: #fff;
  margin-bottom: 8px;
  letter-spacing: -0.5px;
}

.brand-desc {
  color: rgba(255,255,255,0.45);
  font-size: 0.95rem;
  line-height: 1.6;
  margin-bottom: 36px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: rgba(255,255,255,0.55);
  font-size: 0.85rem;
}

.feature-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.feature-dot.love  { background: var(--accent-love); box-shadow: 0 0 8px var(--accent-love); }
.feature-dot.manus { background: var(--accent-manus); box-shadow: 0 0 8px var(--accent-manus); }
.feature-dot.multi { background: var(--accent-primary); box-shadow: 0 0 8px var(--accent-primary); }

.brand-footer {
  position: absolute;
  bottom: 28px;
  color: rgba(255,255,255,0.2);
  font-size: 0.75rem;
  z-index: 1;
}

/* ===== Right: Form panel ===== */
.login-form-side {
  width: 460px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-app);
  border-left: 1px solid var(--border-secondary);
  padding: 48px;
}

.form-wrapper {
  width: 100%;
  max-width: 340px;
}

.form-wrapper h2 {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.form-subtitle {
  color: var(--text-tertiary);
  font-size: 0.85rem;
  margin-bottom: 32px;
}

/* ===== Form fields ===== */
.field {
  margin-bottom: 20px;
}

.field-label {
  display: block;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  color: var(--text-tertiary);
  pointer-events: none;
  transition: color 0.2s;
}

.input-wrap input {
  width: 100%;
  padding: 12px 14px 12px 44px;
  border: 1px solid var(--border-input);
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 14px;
  transition: var(--transition);
}

.input-wrap input::placeholder {
  color: var(--text-tertiary);
}

.input-wrap input:focus {
  border-color: var(--border-input-focus);
  background: var(--bg-input-focus);
  box-shadow: 0 0 0 3px rgba(31, 111, 235, 0.1);
}

.input-wrap input:focus + .input-icon,
.input-wrap:has(input:focus) .input-icon {
  color: var(--accent-primary);
}

/* ===== Submit button ===== */
.submit-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--accent-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s ease;
}

.submit-btn:hover:not(:disabled) {
  background: var(--accent-primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(31, 111, 235, 0.3);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== Error ===== */
.error-msg {
  color: var(--danger);
  font-size: 0.8rem;
  text-align: center;
  margin-top: 14px;
  background: var(--danger-bg);
  padding: 8px 14px;
  border-radius: var(--radius-xs);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== Switch mode ===== */
.switch-mode {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--border-secondary);
  text-align: center;
  font-size: 0.85rem;
  color: var(--text-tertiary);
}

.switch-btn {
  color: var(--accent-primary);
  font-weight: 600;
  font-size: 0.85rem;
  margin-left: 4px;
}

.switch-btn:hover {
  color: var(--accent-primary-hover);
  text-decoration: underline;
}

/* ===== Responsive ===== */
@media (max-width: 900px) {
  .login-brand {
    display: none;
  }
  .login-form-side {
    width: 100%;
    border-left: none;
  }
}

@media (max-width: 480px) {
  .login-form-side {
    padding: 24px;
  }
}
</style>
