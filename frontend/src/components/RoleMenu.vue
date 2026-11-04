<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session } from '@/store/session'

/**
 * 角色菜单（FR-02 角色区分与权限控制）。
 *
 * <p>★ 普通用户登录后<strong>看不到</strong>用户管理／会议室管理／全部预约处理入口
 * （FR-02 的原文要求）。管理员登录后能看到对应管理入口。</p>
 *
 * <p>第 10 周变化：管理端 4 个入口从"建设中"占位变成<strong>真实可点的路由</strong>；
 * 普通用户菜单补上「我的预约」（八步的第 6／7／8 步要它）。</p>
 *
 * <p>⚠️ <b>前端隐藏菜单不是安全边界</b>——菜单可以被绕过（F12 改 DOM、直接调接口），
 * 真正的边界在后端 {@code AuthInterceptor}：普通用户调 {@code /api/admin/**}
 * 一律返回 403（NF-03）。前端的角色菜单只是"不让用户看见用不上的功能"，
 * 属于体验层，不是权限层。</p>
 */
const router = useRouter()
const route = useRoute()

const user = computed(() => session.user)
const isAdmin = computed(() => user.value?.role === 'ADMIN')

interface MenuItem {
  name: string
  label: string
  route: string
}

/** 普通用户菜单（FR-02：看不到任何管理入口） */
const userMenus: MenuItem[] = [
  { name: 'rooms', label: '会议室查询', route: '/rooms' },
  { name: 'booking-new', label: '提交预约', route: '/bookings/new' },
  { name: 'my-bookings', label: '我的预约', route: '/bookings/mine' }
]

/**
 * 管理员菜单：在普通用户菜单基础上加管理入口（FR-02）。
 *
 * <p>★ 第 10 周这 4 个入口从"建设中"变成真实路由。它们<b>只是体验层的门</b>——
 * 地址栏可以被绕过（直接敲 /admin/users），真正的边界是后端
 * {@code AuthInterceptor}：普通用户令牌调 {@code /api/admin/**} 一律 403（NF-03）。
 * 所以路由表里这 4 条都声明了 {@code role: 'ADMIN'}，后端那边也有独立拦截，
 * 两层缺一不可。</p>
 */
const adminMenus: MenuItem[] = [
  { name: 'admin-bookings', label: '全部预约', route: '/admin/bookings' },
  { name: 'admin-rooms', label: '会议室管理', route: '/admin/rooms' },
  { name: 'admin-users', label: '用户管理', route: '/admin/users' },
  { name: 'admin-logs', label: '操作日志', route: '/admin/logs' }
]

const menus = computed<MenuItem[]>(() => (isAdmin.value ? [...userMenus, ...adminMenus] : userMenus))

/** 当前高亮项：按路径前缀匹配，进详情页时仍高亮「会议室查询」 */
function isActive(item: MenuItem): boolean {
  const path = route.path
  if (item.route === '/rooms') {
    return path === '/rooms' || path.startsWith('/rooms/')
  }
  return path === item.route
}

function onMenuClick(item: MenuItem): void {
  if (route.path !== item.route) {
    void router.push(item.route)
  }
}
</script>

<template>
  <nav class="role-menu">
    <span class="role-menu-label">功能</span>
    <button
      v-for="item in menus"
      :key="item.name"
      class="role-menu-item"
      :class="{ 'is-active': isActive(item) }"
      type="button"
      @click="onMenuClick(item)"
    >
      {{ item.label }}
    </button>
  </nav>
</template>

<style scoped>
.role-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.role-menu-label {
  font-size: 13px;
  color: #909399;
  margin-right: 4px;
}

.role-menu-item {
  border: 1px solid #dcdfe6;
  background: #ffffff;
  color: #606266;
  border-radius: 4px;
  padding: 5px 12px;
  font-size: 14px;
  line-height: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.role-menu-item:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background: #ecf5ff;
}

.role-menu-item.is-active {
  color: #409eff;
  border-color: #409eff;
  background: #ecf5ff;
  font-weight: 500;
}
</style>
