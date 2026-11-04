import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

// 技术栈清单要求 Element Plus 2.7.x，这里统一注册并固定中文语言包。
// main.css 在 Element Plus 样式之后引入，用于覆盖主题色与项目自有排版。
createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
