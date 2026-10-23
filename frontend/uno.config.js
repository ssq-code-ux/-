import { defineConfig, presetWind, presetAttributify, presetIcons } from 'unocss'

export default defineConfig({
  // 暗色模式：通过 class="dark" 切换
  presets: [
    presetWind(),
    presetAttributify(),
    presetIcons({
      scale: 1.2,
      warn: true,
    }),
  ],

  // 快捷组合
  shortcuts: {
    // ── Glass 玻璃态 ──
    'glass':
      'bg-white/60 backdrop-blur-xl border border-white/30 shadow-glass rounded-xl',
    'glass-dark':
      'dark:bg-gray-900/60 dark:border-white/8 dark:shadow-glass-dark',
    'glass-card':
      'glass glass-dark transition-all duration-300 hover:-translate-y-0.5 hover:shadow-glass-hover',

    // ── 按钮 ──
    'btn-glass':
      'glass glass-dark px-4 py-2 rounded-full font-600 text-sm cursor-pointer transition-all duration-200 active:scale-96 hover:shadow-glass-hover',
    'btn-primary':
      'bg-primary text-white px-5 py-2.5 rounded-full font-600 text-sm cursor-pointer border-none transition-all duration-200 hover:opacity-90 active:scale-96',

    // ── 卡片状态色条 ──
    'status-pending': 'border-l-4 border-l-gray-400',
    'status-submitted': 'border-l-4 border-l-amber-400',
    'status-graded': 'border-l-4 border-l-green-500',
    'status-redo': 'border-l-4 border-l-red-400',

    // ── 骨架屏 ──
    'skeleton': 'bg-gray-200 dark:bg-gray-700 rounded animate-pulse',

    // ── 页面容器 ──
    'page-container': 'max-w-5xl mx-auto px-4 py-6',
  },

  // 主题颜色
  theme: {
    colors: {
      primary: '#409eff',
      'primary-light': '#79bbff',
      'primary-dark': '#337ecc',
      success: '#67c23a',
      warning: '#e6a23c',
      danger: '#f56c6c',
      info: '#909399',
    },
    boxShadow: {
      glass: '0 8px 32px rgba(0, 0, 0, 0.06)',
      'glass-dark': '0 8px 32px rgba(0, 0, 0, 0.3)',
      'glass-hover': '0 12px 40px rgba(0, 0, 0, 0.1)',
    },
  },

  // 安全列表：确保动态 class 在构建中被保留
  safelist: [
    'status-pending',
    'status-submitted',
    'status-graded',
    'status-redo',
    'border-l-gray-400',
    'border-l-amber-400',
    'border-l-green-500',
    'border-l-red-400',
  ],
})
