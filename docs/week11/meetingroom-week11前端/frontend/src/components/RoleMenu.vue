<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '@/api/auth'
import { clearSession, isAdmin, sessionUser } from '@/session/session'
import { roleLabel } from '@/utils/labels'

interface MenuItem {
  label: string
  to: string
}

const route = useRoute()
const router = useRouter()
const loggingOut = ref(false)

/**
 * FR-02：普通用户不渲染管理入口；管理员渲染管理入口。
 * 角色说明的权限矩阵中管理员没有“查看我的预约”，因此管理员菜单不放“我的预约”，
 * 只保留会议室查询与管理类入口。
 */
const menuItems = computed<MenuItem[]>(() => {
  if (isAdmin.value) {
    return [
      { label: '会议室查询', to: '/rooms' },
      { label: '全部预约处理', to: '/admin/bookings' },
      { label: '会议室管理', to: '/admin/rooms' },
      { label: '用户管理', to: '/admin/users' },
      { label: '操作日志', to: '/admin/logs' }
    ]
  }

  return [
    { label: '会议室查询', to: '/rooms' },
    { label: '我的预约', to: '/bookings/mine' }
  ]
})

function isActive(path: string): boolean {
  if (path === '/rooms') {
    return (
      route.path === '/rooms' ||
      route.path.startsWith('/rooms/') ||
      route.path === '/bookings/new'
    )
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true

  try {
    await logout()
  } catch {
    // 网络异常也要完成本地登出；服务端令牌最迟在 TTL 到期后失效
  } finally {
    clearSession()
    loggingOut.value = false
    await router.replace('/login')
  }
}
</script>

<template>
  <header class="topbar">
    <div class="topbar__inner">
      <RouterLink to="/rooms" class="brand" aria-label="返回会议室列表">
        智能会议室管理系统
        <span class="brand__version">V1.0</span>
      </RouterLink>

      <nav class="nav" aria-label="主菜单">
        <RouterLink
          v-for="item in menuItems"
          :key="item.to"
          :to="item.to"
          class="nav__link"
          :class="{ 'nav__link--active': isActive(item.to) }"
          :aria-current="isActive(item.to) ? 'page' : undefined"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div v-if="sessionUser" class="topbar__user">
        <span class="user-chip">
          {{ sessionUser.name }}
          <span class="user-chip__role">{{ roleLabel(sessionUser.role) }}</span>
        </span>
        <el-button
          class="btn btn--ghost btn--small"
          native-type="button"
          :disabled="loggingOut"
          @click="handleLogout"
        >
          {{ loggingOut ? '退出中…' : '退出登录' }}
        </el-button>
      </div>
    </div>
  </header>
</template>
