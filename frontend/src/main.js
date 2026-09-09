/**
 * 应用入口文件（main.js）：浏览器加载页面后最先执行的代码，负责“搭台”。
 * 步骤：创建 Vue 根应用 → 安装 Pinia（状态管理）与 Router（路由）→
 * 注册 Element Plus（UI 组件库，locale 传 zh-cn 让日期选择器等文案显示中文）
 * → 最后把根组件 App.vue 挂载到 index.html 的 #app 节点上，应用才真正渲染出来。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// Element Plus 自带样式（须在项目覆盖样式之前引入，style.css 才能顺利覆盖主题色）
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

// 创建根应用实例（App.vue 为根组件）
const app = createApp(App)
// 依次安装全局插件：Pinia 状态管理（提供 useUserStore 等 store）
app.use(createPinia())
// Vue Router 路由（所有页面跳转与登录守卫都由此接管）
app.use(router)
app.use(ElementPlus, { locale: zhCn })
// 挂载到 <div id="app">：mount 执行后 Vue 接管页面，开始渲染根组件
app.mount('#app')
