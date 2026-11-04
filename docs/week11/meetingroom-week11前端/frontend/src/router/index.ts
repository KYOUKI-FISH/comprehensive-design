import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, sessionUser } from '@/session/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' }
    },
    {
      path: '/',
      redirect: '/rooms'
    },
    {
      path: '/rooms',
      name: 'rooms',
      component: () => import('@/views/RoomListView.vue'),
      meta: { requiresAuth: true, title: '会议室列表' }
    },
    {
      path: '/rooms/:id(\\d+)',
      name: 'room-detail',
      component: () => import('@/views/RoomDetailView.vue'),
      props: true,
      meta: { requiresAuth: true, title: '会议室详情' }
    },
    {
      path: '/bookings/new',
      name: 'booking-new',
      component: () => import('@/views/BookingView.vue'),
      meta: { requiresAuth: true, role: 'USER', title: '预约会议室' }
    },
    {
      path: '/bookings/mine',
      name: 'booking-mine',
      component: () => import('@/views/MyBookingsView.vue'),
      meta: { requiresAuth: true, role: 'USER', title: '我的预约' }
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: () => import('@/views/AdminUsersView.vue'),
      meta: { requiresAuth: true, role: 'ADMIN', title: '用户管理' }
    },
    {
      path: '/admin/rooms',
      name: 'admin-rooms',
      component: () => import('@/views/AdminRoomsView.vue'),
      meta: { requiresAuth: true, role: 'ADMIN', title: '会议室管理' }
    },
    {
      path: '/admin/bookings',
      name: 'admin-bookings',
      component: () => import('@/views/AdminBookingsView.vue'),
      meta: { requiresAuth: true, role: 'ADMIN', title: '全部预约处理' }
    },
    {
      path: '/admin/logs',
      name: 'admin-logs',
      component: () => import('@/views/AdminLogsView.vue'),
      meta: { requiresAuth: true, role: 'ADMIN', title: '操作日志' }
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { requiresAuth: true, title: '无权访问' }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/rooms'
    }
  ]
})

router.beforeEach((to) => {
  const loggedIn = isLoggedIn.value

  if (to.meta.public && loggedIn) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/rooms'
    return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/rooms'
  }

  if (to.meta.requiresAuth && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.role && sessionUser.value?.role !== to.meta.role) {
    return { path: '/403', query: { reason: to.meta.role === 'USER' ? 'role' : 'admin' } }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 智能会议室管理系统`
    : '智能会议室管理系统'
})

export default router
