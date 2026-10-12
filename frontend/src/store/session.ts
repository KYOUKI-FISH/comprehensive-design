import { reactive, readonly } from 'vue'
import type { LoginResponse } from '@/types/api'

/**
 * 登录态（骨架级，<b>仅存内存</b>）。
 *
 * <p>⚠ 已知限制：刷新页面即丢失登录态，需要重新登录。
 * 这是第 6 周刻意的临时状态——后端本周不发令牌／不建会话，前端也就无处可存。
 * 待第 9～10 周登录鉴权体系落地后，再改为令牌 + 持久化存储。
 * <b>不要把这里改成 sessionStorage/localStorage 假装持久化</b>，那只会掩盖鉴权缺失。
 */
interface SessionState {
  user: LoginResponse | null
}

const state = reactive<SessionState>({ user: null })

/** 只读的登录态，供组件渲染使用 */
export const session = readonly(state)

/** 登录成功后写入登录态 */
export function setSession(user: LoginResponse): void {
  state.user = user
}

/** 退出登录时清空登录态 */
export function clearSession(): void {
  state.user = null
}

/** 是否已登录（路由守卫用） */
export function isLoggedIn(): boolean {
  return state.user !== null
}
