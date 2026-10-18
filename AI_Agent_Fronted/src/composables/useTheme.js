import { computed, ref, watch } from 'vue'

const STORAGE_KEY = 'ai-agent-theme-mode'
const LEGACY_STORAGE_KEY = 'ai-agent-theme'
const VALID_MODES = ['system', 'light', 'dark']
const THEME_COLORS = {
  dark: '#0B0D0C',
  light: '#F6F4EE'
}

// localStorage 在隐私模式等场景可能不可用，统一做容错处理
function getStorageItem(key) {
  try {
    return typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null
  } catch {
    return null
  }
}

function setStorageItem(key, value) {
  try {
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value)
  } catch {
    // 忽略存储失败，主题仍可在当前会话内正常使用
  }
}

function removeStorageItem(key) {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(key)
  } catch {
    // 忽略存储失败
  }
}

// 优先读取新 key；旧 key 仅作为一次性迁移来源，默认跟随系统
function readInitialMode() {
  const storedMode = getStorageItem(STORAGE_KEY)
  if (VALID_MODES.includes(storedMode)) return storedMode

  const legacyTheme = getStorageItem(LEGACY_STORAGE_KEY)
  if (legacyTheme === 'light' || legacyTheme === 'dark') {
    setStorageItem(STORAGE_KEY, legacyTheme)
    removeStorageItem(LEGACY_STORAGE_KEY)
    return legacyTheme
  }

  return 'system'
}

const mode = ref(readInitialMode())

// matchMedia 的 matches 不是响应式的，用 ref 记录系统偏好
const systemMediaQuery =
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null
const systemPrefersDark = ref(Boolean(systemMediaQuery?.matches))

const resolvedTheme = computed(() => {
  if (mode.value === 'system') return systemPrefersDark.value ? 'dark' : 'light'
  return mode.value
})
const theme = computed(() => resolvedTheme.value)
const isSystem = computed(() => mode.value === 'system')

// 将解析后的主题同步到根节点与浏览器主题色；首屏脚本也会做同样的初始设置
function applyResolvedTheme() {
  if (typeof document === 'undefined') return

  const root = document.documentElement
  root.setAttribute('data-theme', resolvedTheme.value)
  root.style.colorScheme = resolvedTheme.value

  const themeColorMeta = document.querySelector('meta[name="theme-color"]')
  if (themeColorMeta) {
    themeColorMeta.setAttribute('content', THEME_COLORS[resolvedTheme.value])
  }
}

let mediaListenerRegistered = false

function handleSystemThemeChange(event) {
  // 只有跟随系统时才让系统偏好影响 resolvedTheme
  if (mode.value !== 'system') return
  systemPrefersDark.value = Boolean(event.matches)
}

// 模块级单例只需要注册一次；重复调用是幂等的
function ensureSystemThemeListener() {
  if (!systemMediaQuery || mediaListenerRegistered) return

  if (typeof systemMediaQuery.addEventListener === 'function') {
    systemMediaQuery.addEventListener('change', handleSystemThemeChange)
  } else if (typeof systemMediaQuery.addListener === 'function') {
    // 兼容旧版 Safari
    systemMediaQuery.addListener(handleSystemThemeChange)
  }
  mediaListenerRegistered = true
}

function setMode(nextMode) {
  if (!VALID_MODES.includes(nextMode)) return

  // 进入跟随系统时立即读取一次最新偏好，避免使用旧缓存
  if (nextMode === 'system' && systemMediaQuery) {
    systemPrefersDark.value = Boolean(systemMediaQuery.matches)
  }
  mode.value = nextMode
}

// system -> light -> dark -> system
function toggleTheme() {
  const nextMode = {
    system: 'light',
    light: 'dark',
    dark: 'system'
  }[mode.value]
  setMode(nextMode)
}

watch(resolvedTheme, applyResolvedTheme, { immediate: true, flush: 'sync' })

// 持久化新 key，并清理旧 key，完成迁移
watch(
  mode,
  (value) => {
    setStorageItem(STORAGE_KEY, value)
    removeStorageItem(LEGACY_STORAGE_KEY)
  },
  { immediate: true, flush: 'sync' }
)

ensureSystemThemeListener()

export function useTheme() {
  // 再次调用时不会重复注册监听器
  ensureSystemThemeListener()

  return {
    mode,
    resolvedTheme,
    theme,
    setMode,
    toggleTheme,
    isSystem
  }
}