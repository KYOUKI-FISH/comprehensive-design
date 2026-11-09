<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RoleMenu from '@/components/RoleMenu.vue'
import { isLoggedIn } from '@/session/session'

const route = useRoute()
const router = useRouter()

const showMenu = computed(() => Boolean(isLoggedIn.value) && !route.meta.public)
const isPlainPage = computed(() => Boolean(route.meta.public))

function handleAuthExpired() {
  if (route.path === '/login') return
  router.replace({ path: '/login', query: { reason: 'expired' } })
}

onMounted(() => window.addEventListener('auth:expired', handleAuthExpired))
onUnmounted(() => window.removeEventListener('auth:expired', handleAuthExpired))
</script>

<template>
  <div class="app-shell">
    <RoleMenu v-if="showMenu" />

    <main :class="['app-main', { 'app-main--plain': isPlainPage }]">
      <RouterView />
    </main>

    <footer v-if="!isPlainPage" class="app-footer">
      智能会议室管理系统 · V1.0 · 第10周核心闭环
    </footer>
  </div>
</template>
