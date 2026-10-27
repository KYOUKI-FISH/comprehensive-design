import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import RoomListView from '@/views/RoomListView.vue'
import RoomDetailView from '@/views/RoomDetailView.vue'
import BookingView from '@/views/BookingView.vue'
import { isLoggedIn, restoreSession } from '@/store/session'

/**
 * 路由表（第 9 周）。
 *
 * <p>第 6 周骨架阶段只有登录、会议室查询两条真实路由；第 9 周按任务书补上
 * <strong>会议室详情页</strong>与<strong>预约表单页</strong>，凑齐"四个页面"：
 * 登录页、会议室列表页、会议室详情页、预约表单页。</p>
 *
 * <p>管理后台、我的预约属第 10 周，路由到时再补。</p>
 */
const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'rooms' } },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { public: true, title: '登录' }
  },
  {
    path: '/rooms',
    name: 'rooms',
    component: RoomListView,
    meta: { title: '会议室查询' }
  },
  {
    path: '/rooms/:id',
    name: 'room-detail',
    component: RoomDetailView,
    meta: { title: '会议室详情' }
  },
  {
    path: '/bookings/new',
    name: 'booking-new',
    component: BookingView,
    meta: { title: '提交预约' }
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'rooms' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫：未登录只放行 public 路由。
 *
 * <p>第 9 周起守卫会先尝试<strong>从 localStorage 恢复登录态</strong>——
 * 否则刷新页面后虽然 localStorage 里有令牌，内存里的登录态却是空的，
 * 会被守卫弹回登录页（而令牌其实还有效），这正是"刷新不掉登录"要解决的问题。</p>
 *
 * <p>⚠️ 注意这只是页面导航层面的门，不是安全边界——真正的权限校验在
 * 后端 {@code AuthInterceptor}（前端守卫可被绕过，NF 安全类要求以后端为准）。</p>
 */
router.beforeEach((to) => {
  if (to.meta.public === true) {
    return true
  }
  if (!isLoggedIn()) {
    // 内存里没有登录态，但 localStorage 可能有令牌 → 先尝试恢复
    if (restoreSession()) {
      return true
    }
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
