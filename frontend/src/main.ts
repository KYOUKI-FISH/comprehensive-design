import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from '@/App.vue'
import router from '@/router'
import '@/styles/main.css'

/**
 * 前端入口（第 6 周工程骨架）。
 * 架构 A2 前后端分离：本 SPA 只负责页面与交互，业务数据全部来自后端 REST 接口。
 */
createApp(App)
  .use(router)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app')
