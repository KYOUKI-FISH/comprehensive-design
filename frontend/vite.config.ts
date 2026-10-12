import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 智能会议室管理系统 V1.0 —— 前端构建配置（第 6 周工程骨架）
// 架构：A2 前后端分离。本进程只提供 SPA 资源，后端接口在 http://localhost:8080。
// 注意：本项目【未】使用 Vite 代理转发接口，前端直连后端 8080 端口，
//       跨域由后端 WebConfig 的 CORS 配置放行 —— 这是《技术选型报告》里
//       "选 A2 会遇到的问题"的正面处理方式，联调时可直接观察到跨域是否配对。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 与后端 CORS 白名单（WebConfig.FRONTEND_ORIGIN）一致，改这里要同步改那里
    port: 5173,
    strictPort: true,
    open: false
  }
})
