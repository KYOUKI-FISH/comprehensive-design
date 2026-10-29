import 'vue-router'

/**
 * 路由 meta 的类型声明（第 9 周）。
 *
 * <p>把守卫里用到的字段显式声明出来，避免 {@code to.meta.role} 被推断成 unknown
 * 而通不过 {@code vue-tsc --noEmit} 的严格检查。</p>
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** true = 未登录也放行（只有登录页） */
    public?: boolean
    /** 页面标题，用于顶栏与 document.title */
    title?: string
    /**
     * 允许访问的角色（FR-02）。
     * 不写 = 登录即可访问；写了且当前角色不符 → 跳 403。
     */
    role?: 'USER' | 'ADMIN'
  }
}
