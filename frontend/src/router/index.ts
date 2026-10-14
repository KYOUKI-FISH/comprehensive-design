import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import RoomListView from '@/views/RoomListView.vue'
import { isLoggedIn } from '@/store/session'

/**
 * 路由表（骨架级）。
 *
 * <p>本周只有两条真实路由：登录、会议室查询。管理后台、我的预约、提交预约等
 * 属第 9～10 周，路由到时再补。
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
  { path: '/:pathMatch(.*)*', redirect: { name: 'rooms' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 极简路由守卫：未登录只放行 public 路由。
 *
 * <p>注意这只是"页面导航层面的门"，不是安全边界——真正的权限校验第 9～10 周
 * 放在后端接口上（前端守卫可被绕过，NF 安全类要求以后端为准）。
 */
router.beforeEach((to) => {
  if (to.meta.public === true) {
    return true
  }
  if (!isLoggedIn()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
