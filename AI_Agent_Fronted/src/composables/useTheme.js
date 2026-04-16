import { ref, watch } from 'vue'

const STORAGE_KEY = 'ai-agent-theme'

// Module-level singleton — shared across all components
const theme = ref(localStorage.getItem(STORAGE_KEY) || 'dark')

// Apply theme immediately on import
if (typeof document !== 'undefined') {
  document.documentElement.setAttribute('data-theme', theme.value)
}

export function useTheme() {
  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  watch(theme, (val) => {
    document.documentElement.setAttribute('data-theme', val)
    localStorage.setItem(STORAGE_KEY, val)
  })

  return { theme, toggleTheme }
}
