<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title ?? '功能建设中')
const note = computed(
  () => route.meta.note ?? '该功能将在后续周次实现，本周先保留入口占位。'
)
</script>

<template>
  <section class="page page--narrow">
    <header class="page-head">
      <p class="eyebrow">建设中</p>
      <h1 class="page-title">{{ title }}</h1>
    </header>

    <div class="card card--pad placeholder-card">
      <p>{{ note }}</p>
      <p class="muted">
        第 9 周交付范围为：登录页、会议室列表页、会议室详情页、预约表单页四个可运行页面。
      </p>
      <RouterLink class="btn btn--primary" to="/rooms">返回会议室列表</RouterLink>
    </div>
  </section>
</template>
