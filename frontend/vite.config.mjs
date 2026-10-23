import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { writeFileSync, mkdirSync } from 'fs'

const BUILD_TIME = new Date().toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
const BUILD_TAG = Date.now().toString(36)

// 自定义插件：生成 version.json 供前端轮询检测新版本
function generateVersion() {
  return {
    name: 'generate-version',
    closeBundle() {
      // dist 目录不存在时先创建，避免构建末尾写 version.json 报 ENOENT
      mkdirSync('dist', { recursive: true })
      writeFileSync('dist/version.json', JSON.stringify({ time: BUILD_TIME, tag: BUILD_TAG }))
    },
  }
}

export default defineConfig({
  plugins: [vue(), UnoCSS(), generateVersion()],
  build: {
    // vendor 分包：大依赖独立成 chunk，升级业务代码时用户可复用浏览器缓存
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-element': ['element-plus'],
          // echarts 按需引入后不再依赖整包，分包键同步改为实际引入的子模块
          'vendor-echarts': ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'],
        },
      },
    },
    chunkSizeWarningLimit: 1200,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // Gateway 统一入口
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  define: {
    __BUILD_TIME__: JSON.stringify(BUILD_TIME),
    __BUILD_TAG__: JSON.stringify(BUILD_TAG),
  },
})