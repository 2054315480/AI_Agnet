import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/http.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../components/LoginView.vue'),
    meta: { title: '登录 · 客服工作台', guest: true }
  },
  {
    path: '/',
    name: 'Chat',
    component: () => import('../components/ChatView.vue'),
    meta: { title: '客服工作台', requiresAuth: true }
  },
  {
    path: '/admin/faq',
    name: 'AdminFAQ',
    component: () => import('../components/AdminFAQView.vue'),
    meta: { title: 'FAQ 管理 · 客服工作台', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/console',
    name: 'Console',
    component: () => import('../components/SystemConsole.vue'),
    meta: { title: '系统能力控制台 · 客服工作台', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/experiments',
    name: 'Experiments',
    component: () => import('../components/ExperimentCenter.vue'),
    meta: { title: '实验中心 · 客服工作台', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/analytics',
    name: 'Analytics',
    component: () => import('../components/AnalyticsView.vue'),
    meta: { title: '对话分析 · 客服工作台', requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title || '客服工作台'
  const token = getToken()
  if (to.meta.requiresAuth && !token) {
    return { name: 'Login' }
  }
  if (to.meta.guest && token) {
    return { name: 'Chat' }
  }
})

export default router
