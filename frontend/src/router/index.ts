import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import RoomListView from '@/views/RoomListView.vue'
import RoomDetailView from '@/views/RoomDetailView.vue'
import BookingView from '@/views/BookingView.vue'
import ForbiddenView from '@/views/ForbiddenView.vue'
import MyBookingsView from '@/views/MyBookingsView.vue'
import AdminBookingsView from '@/views/AdminBookingsView.vue'
import AdminRoomsView from '@/views/AdminRoomsView.vue'
import AdminUsersView from '@/views/AdminUsersView.vue'
import AdminLogsView from '@/views/AdminLogsView.vue'
import { isLoggedIn, restoreSession, session } from '@/store/session'

/**
 * 路由表。
 *
 * <p>第 6 周骨架阶段只有登录、会议室查询两条真实路由；第 9 周按任务书补上
 * <strong>会议室详情页</strong>与<strong>预约表单页</strong>，凑齐"四个页面"：
 * 登录页、会议室列表页、会议室详情页、预约表单页。</p>
 *
 * <p>★ 第 10 周补上两条闭环所需的 5 个页面：
 * <ul>
 *   <li><b>我的预约</b>（{@code /bookings/mine}）—— 八步的第 6／7／8 步要它；</li>
 *   <li><b>管理端 4 页</b>（{@code /admin/**}）—— 管理员四步要它，且必须声明
 *       {@code role: 'ADMIN'}，否则普通用户能直接敲地址进去。</li>
 * </ul>
 * </p>
 *
 * <p>⚠️ {@code meta.role} 只是<b>导航层的门</b>，不是安全边界：地址栏可以被绕过，
 * 真正的边界是后端 {@code AuthInterceptor}（普通用户令牌调 {@code /api/admin/**}
 * 一律 403，NF-03）。两边都要有，缺一不可——前端这层管体验，后端那层管安全。</p>
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
    // ★ FR-02：角色说明的权限矩阵中管理员"提交预约：不允许"，
    //   因此本页只放行普通用户。这只是导航层的门，真正的边界在后端。
    meta: { title: '提交预约', role: 'USER' }
  },
  {
    path: '/bookings/mine',
    name: 'my-bookings',
    component: MyBookingsView,
    // 不设 role：管理员也可能有自己订过的预约，看自己的记录不构成越权
    // （管理员看全部预约走 /admin/bookings，那才是受限入口）。
    meta: { title: '我的预约' }
  },
  {
    path: '/admin/bookings',
    name: 'admin-bookings',
    component: AdminBookingsView,
    meta: { title: '全部预约', role: 'ADMIN' }
  },
  {
    path: '/admin/rooms',
    name: 'admin-rooms',
    component: AdminRoomsView,
    meta: { title: '会议室管理', role: 'ADMIN' }
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: AdminUsersView,
    meta: { title: '用户管理', role: 'ADMIN' }
  },
  {
    path: '/admin/logs',
    name: 'admin-logs',
    component: AdminLogsView,
    meta: { title: '操作日志', role: 'ADMIN' }
  },
  {
    path: '/403',
    name: 'forbidden',
    component: ForbiddenView,
    meta: { title: '无权访问' }
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'rooms' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫：未登录只放行 public 路由；已登录再按角色过滤。
 *
 * <p>第 9 周起守卫会先尝试<strong>从 localStorage 恢复登录态</strong>——
 * 否则刷新页面后虽然 localStorage 里有令牌，内存里的登录态却是空的，
 * 会被守卫弹回登录页（而令牌其实还有效），这正是"刷新不掉登录"要解决的问题。</p>
 *
 * <p>⚠️ 注意这只是页面导航层面的门，不是安全边界——真正的权限校验在
 * 后端 {@code AuthInterceptor}（前端守卫可被绕过，NF 安全类要求以后端为准）。
 * 因此普通用户令牌直调管理接口、管理员令牌直调 {@code POST /api/bookings}，
 * 都必须由后端返回 403（NF-03）。</p>
 */
router.beforeEach((to) => {
  if (to.meta.public === true) {
    return true
  }
  if (!isLoggedIn()) {
    // 内存里没有登录态，但 localStorage 可能有令牌 → 先尝试恢复
    if (!restoreSession()) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  // ★ FR-02：路由声明了 role 时，当前角色不匹配就跳 403 页
  const requiredRole = to.meta.role
  if (requiredRole && session.user?.role !== requiredRole) {
    return { name: 'forbidden', query: { from: to.fullPath } }
  }
  return true
})

export default router
