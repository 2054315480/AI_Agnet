import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../components/LoginView.vue'),
    meta: { title: '登录 - AI Agent', guest: true }
  },
  {
    path: '/',
    name: 'Chat',
    component: () => import('../components/ChatView.vue'),
    meta: { title: 'AI Agent', requiresAuth: true }
  },
  {
    path: '/admin/faq',
    name: 'AdminFAQ',
    component: () => import('../components/AdminFAQView.vue'),
    meta: { title: 'FAQ 管理 - AI Agent', requiresAuth: true, requiresAdmin: true }
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
  document.title = to.meta.title || 'AI Agent'
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    return { name: 'Login' }
  }
  if (to.meta.guest && token) {
    return { name: 'Chat' }
  }
})

export default router
