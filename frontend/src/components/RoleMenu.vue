<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { session } from '@/store/session'

/**
 * 角色菜单（第 9 周；对应 FR-02 角色区分与权限控制）。
 *
 * <p>★ 普通用户登录后<strong>看不到</strong>用户管理／会议室管理／全部预约处理入口
 * （FR-02 的原文要求）。管理员登录后能看到对应管理入口。</p>
 *
 * <p>⚠️ <b>前端隐藏菜单不是安全边界</b>——菜单可以被绕过（F12 改 DOM、直接调接口），
 * 真正的边界在后端 {@code AuthInterceptor}：普通用户调 {@code /api/admin/**}
 * 一律返回 403（NF-03）。前端的角色菜单只是"不让用户看见用不上的功能"，
 * 属于体验层，不是权限层。</p>
 *
 * <p>第 9 周的管理端页面尚未实现（属第 10 周），因此管理员入口本期<strong>显示但标注建设中</strong>，
 * 点击给出提示而不是跳到空白页。</p>
 */
const router = useRouter()

const user = computed(() => session.user)
const isAdmin = computed(() => user.value?.role === 'ADMIN')

interface MenuItem {
  name: string
  label: string
  route?: string
  /** 尚未实现的页面：显示为"建设中"，点击给提示 */
  pending?: boolean
}

/** 普通用户菜单 */
const userMenus: MenuItem[] = [
  { name: 'rooms', label: '会议室查询', route: '/rooms' },
  { name: 'booking-new', label: '提交预约', route: '/bookings/new' }
]

/** 管理员菜单：在普通用户菜单基础上加管理入口（FR-02） */
const adminMenus: MenuItem[] = [
  { name: 'admin-bookings', label: '全部预约', pending: true },
  { name: 'admin-rooms', label: '会议室管理', pending: true },
  { name: 'admin-users', label: '用户管理', pending: true },
  { name: 'admin-logs', label: '操作日志', pending: true }
]

const menus = computed<MenuItem[]>(() => (isAdmin.value ? [...userMenus, ...adminMenus] : userMenus))

function onMenuClick(item: MenuItem): void {
  if (item.pending) {
    // 第 9 周不实现管理端页面（第 10 周做），点一下给明确提示而不是空白页
    window.alert(`「${item.label}」将在第 10 周实现（管理员功能）。\n\n第 9 周的最小原型范围是：登录 → 会议室列表 → 会议室详情 → 提交预约。`)
    return
  }
  if (item.route) {
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
      :class="{ 'is-pending': item.pending }"
      type="button"
      @click="onMenuClick(item)"
    >
      {{ item.label }}
      <span v-if="item.pending" class="role-menu-badge">建设中</span>
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

.role-menu-item.is-pending {
  color: #909399;
  border-style: dashed;
}

.role-menu-badge {
  font-size: 11px;
  margin-left: 4px;
  color: #e6a23c;
}
</style>
