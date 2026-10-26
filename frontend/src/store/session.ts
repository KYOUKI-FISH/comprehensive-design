import { reactive, readonly } from 'vue'
import type { LoginResponse } from '@/types/api'

/**
 * 登录态与会话令牌（第 9 周）。
 *
 * <p>★ 与第 6 周骨架版的区别（重要，别照着旧注释理解）：</p>
 * <p>第 6 周时后端<strong>不发令牌</strong>，所以登录态只能存内存，刷新即丢，
 * 当时特意留了一句"不要改成 sessionStorage 假装持久化"——那句话是<strong>为了防止
 * 用假持久化掩盖"后端根本没有鉴权"这个事实</strong>。</p>
 * <p>第 9 周起后端<strong>已经签发令牌并有鉴权拦截器</strong>（{@code AuthInterceptor}
 * 会校验 {@code Authorization: Bearer <token>}），前提变了，
 * 因此<strong>改成持久化是正当的、也是必须的</strong>——否则刷新页面就掉登录，
 * 演示第一跳就失败。</p>
 *
 * <p>存储位置用 {@code localStorage}（不是 sessionStorage）：本项目要求
 * "刷新页面不掉登录"，sessionStorage 关掉标签页就没了，不满足。</p>
 */
interface SessionState {
  user: LoginResponse | null
}

const state = reactive<SessionState>({ user: null })

/** localStorage 键名 */
const TOKEN_KEY = 'mr_token'
const USER_KEY = 'mr_user'

/** 只读的登录态，供组件渲染使用 */
export const session = readonly(state)

/**
 * 登录成功后写入登录态与令牌。
 *
 * @param user 登录结果（含 token）
 */
export function setSession(user: LoginResponse): void {
  state.user = user
  if (user.token) {
    localStorage.setItem(TOKEN_KEY, user.token)
  }
  localStorage.setItem(USER_KEY, JSON.stringify({
    userId: user.userId,
    username: user.username,
    realName: user.realName,
    role: user.role
  }))
}

/** 退出登录时清空登录态与令牌 */
export function clearSession(): void {
  state.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 是否已登录（路由守卫用） */
export function isLoggedIn(): boolean {
  return state.user !== null
}

/**
 * 取当前会话令牌（请求拦截器用）。
 *
 * @returns 令牌；未登录时返回 null
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 只清掉令牌，不动用户信息。
 *
 * <p>用于"令牌失效"这种场景：后端说 401 了，说明令牌已经不能用，
 * 但界面上的用户名显示没必要一起清掉（清掉也没坏处，这里保留信息便于排查）。</p>
 */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 从 localStorage 恢复登录态（应用启动时调用一次）。
 *
 * <p>只恢复<strong>界面上的用户信息</strong>（用于显示用户名与角色菜单），
 * 令牌是否有效<strong>不由前端判断</strong>——真要发一次请求才知道。
 * 所以恢复出来的状态只是"看起来登录了"，后端若已把令牌作废，
 * 下一次请求会返回 401，请求层会清掉登录态。</p>
 *
 * @returns 是否成功恢复
 */
export function restoreSession(): boolean {
  const token = localStorage.getItem(TOKEN_KEY)
  const rawUser = localStorage.getItem(USER_KEY)
  if (!token || !rawUser) {
    return false
  }
  try {
    const user = JSON.parse(rawUser) as LoginResponse
    state.user = { ...user, token }
    return true
  } catch {
    // 存储内容损坏（手工改过 localStorage 等）→ 当作未登录
    clearSession()
    return false
  }
}
