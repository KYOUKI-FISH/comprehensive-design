import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    requiresAuth?: boolean
    role?: 'USER' | 'ADMIN'
    title?: string
    note?: string
  }
}

export {}
