<template>
  <div class="app-shell">
    <Sidebar v-if="!isLoginPage" :open="sidebarOpen" @toggle="toggleSidebar" />
    <main class="main-area">
      <router-view />
    </main>
    <!-- Mobile overlay -->
    <transition name="fade">
      <div
        v-if="sidebarOpen && isMobile"
        class="sidebar-overlay"
        @click="toggleSidebar"
      />
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, provide, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import { useConversations } from './composables/useConversations.js'
import { useAuth } from './api/auth.js'

const sidebarOpen = ref(true)
const windowWidth = ref(window.innerWidth)
const route = useRoute()

const isMobile = computed(() => windowWidth.value < 768)
const isLoginPage = computed(() => route.path === '/login')

const { isAuthenticated, fetchUser } = useAuth()
const { loadConversations } = useConversations()

// On mobile, sidebar starts closed
onMounted(async () => {
  if (window.innerWidth < 768) {
    sidebarOpen.value = false
  }

  // Restore user session from token
  await fetchUser()

  // Load conversations (will use server API if authenticated)
  loadConversations()

  window.addEventListener('resize', handleResize)
})

// Reload conversations when auth state changes
watch(isAuthenticated, (val) => {
  if (val) {
    loadConversations()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

function handleResize() {
  windowWidth.value = window.innerWidth
  // Auto-collapse on mobile
  if (windowWidth.value < 768 && sidebarOpen.value) {
    sidebarOpen.value = false
  }
  // Auto-expand on desktop
  if (windowWidth.value >= 768 && !sidebarOpen.value) {
    sidebarOpen.value = true
  }
}

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

// Provide sidebar control to child components (for mobile FAB)
provide('sidebarStore', { toggleSidebar })
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-app);
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* ===== Mobile overlay ===== */
.sidebar-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay);
  z-index: 40;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .main-area {
    width: 100%;
  }
}
</style>
