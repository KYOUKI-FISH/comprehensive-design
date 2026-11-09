<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const message = computed(() => {
  if (route.query.reason === 'role') {
    return '当前账号的角色无权访问该页面。普通用户不能进入管理功能；管理员账号不参与个人预约。'
  }
  return '当前账号无权访问该页面。如需调整权限，请联系管理员。'
})
</script>

<template>
  <section class="page page--narrow">
    <header class="page-head">
      <p class="eyebrow">403 Forbidden</p>
      <h1 class="page-title">无权访问</h1>
    </header>

    <div class="card card--pad placeholder-card">
      <p>{{ message }}</p>
      <p class="muted">
        前端菜单隐藏只是使用体验的一部分，后端接口仍会按角色校验并拒绝越权请求（NF-03）。
      </p>
      <RouterLink class="btn btn--primary" to="/rooms">返回会议室列表</RouterLink>
    </div>
  </section>
</template>
