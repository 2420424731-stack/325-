<!--
  App.vue：整个应用的根组件。自身不画任何界面，只提供一个路由出口。
  当前 URL 命中 router/index.js 的哪条路由，这里就渲染哪个页面：
  未登录时是 Login/Register 整页，登录后是带侧栏顶栏的 Layout 布局及其业务子页面。
-->
<template>
  <!-- 路由出口：router-view 会随 URL 变化被自动替换为当前路由对应的页面组件 -->
  <router-view />
</template>
