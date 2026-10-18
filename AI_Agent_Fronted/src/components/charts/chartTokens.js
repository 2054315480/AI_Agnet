const FALLBACKS = {
  accent: '#5E948A',
  accentLove: '#C2A36B',
  success: '#5C8A5F',
  warning: '#A97C3B',
  danger: '#B4544A',
  text: '#E9E6DF',
  textSecondary: 'rgba(233, 230, 223, 0.68)',
  textTertiary: 'rgba(233, 230, 223, 0.44)',
  axisLine: 'rgba(233, 230, 223, 0.16)',
  splitLine: 'rgba(233, 230, 223, 0.07)',
  tooltipBg: '#121514',
  tooltipBorder: 'rgba(233, 230, 223, 0.09)',
  fontFamily: '"Songti SC", "Source Han Serif SC", "Noto Serif SC", STSong, SimSun, Georgia, serif',
  monoFamily: 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
}

/** 读取 CSS 变量并去除首尾空白；空值时回退到安全默认值 */
function readVar(style, name, fallback) {
  const value = style.getPropertyValue(name)
  return value && value.trim() ? value.trim() : fallback
}

/**
 * 读取图表语义色。
 * 每次主题切换后都需要重新读取，才能拿到新的 CSS 变量值。
 */
export function readChartTokens() {
  if (typeof document === 'undefined') {
    return {
      palette: [
        FALLBACKS.accent,
        FALLBACKS.accentLove,
        FALLBACKS.success,
        FALLBACKS.warning,
        FALLBACKS.danger,
        FALLBACKS.textTertiary
      ],
      text: FALLBACKS.text,
      textSecondary: FALLBACKS.textSecondary,
      textTertiary: FALLBACKS.textTertiary,
      axisLine: FALLBACKS.axisLine,
      splitLine: FALLBACKS.splitLine,
      tooltipBg: FALLBACKS.tooltipBg,
      tooltipBorder: FALLBACKS.tooltipBorder,
      fontFamily: FALLBACKS.fontFamily,
      monoFamily: FALLBACKS.monoFamily
    }
  }

  const style = getComputedStyle(document.documentElement)
  const accent = readVar(style, '--accent', FALLBACKS.accent)
  const accentLove = readVar(style, '--accent-love', FALLBACKS.accentLove)
  const success = readVar(style, '--success', FALLBACKS.success)
  const warning = readVar(style, '--warning', FALLBACKS.warning)
  const danger = readVar(style, '--danger', FALLBACKS.danger)
  const textTertiary = readVar(style, '--text-tertiary', FALLBACKS.textTertiary)

  return {
    // 与设计系统的语义顺序保持一致，供图表循环使用
    palette: [accent, accentLove, success, warning, danger, textTertiary],
    text: readVar(style, '--text-primary', FALLBACKS.text),
    textSecondary: readVar(style, '--text-secondary', FALLBACKS.textSecondary),
    textTertiary,
    axisLine: readVar(style, '--border-primary', FALLBACKS.axisLine),
    splitLine: readVar(style, '--rule', FALLBACKS.splitLine),
    tooltipBg: readVar(style, '--bg-sidebar', FALLBACKS.tooltipBg),
    tooltipBorder: readVar(style, '--border-primary', FALLBACKS.tooltipBorder),
    fontFamily: readVar(style, '--font-serif', FALLBACKS.fontFamily),
    monoFamily: readVar(style, '--font-mono', FALLBACKS.monoFamily)
  }
}
