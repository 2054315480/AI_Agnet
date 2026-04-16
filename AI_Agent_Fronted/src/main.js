import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/styles/global.css'

// Initialize theme (sets data-theme on <html>)
import { useTheme } from './composables/useTheme.js'
useTheme()

const app = createApp(App)
app.use(router)
app.mount('#app')
