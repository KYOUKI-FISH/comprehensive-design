import { computed, reactive } from 'vue'
import type { LoginResponse, UserRole, UserVO } from '@/types/api'

const STORAGE_KEY = 'meetingroom.session.v1'
const STORAGE_VERSION = 1
const DEFAULT_TTL_SECONDS = 30 * 60

interface StoredSession {
  version: number
  token: string
  expiresAt: number
  user: UserVO
}

interface SessionState {
  token: string
  expiresAt: number
  user: UserVO | null
}

function readStoredSession(): SessionState {
  const empty: SessionState = { token: '', expiresAt: 0, user: null }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return empty

    const parsed = JSON.parse(raw) as Partial<StoredSession>
    if (
      parsed.version !== STORAGE_VERSION ||
      typeof parsed.token !== 'string' ||
      typeof parsed.expiresAt !== 'number' ||
      !parsed.user ||
      typeof parsed.user !== 'object'
    ) {
      localStorage.removeItem(STORAGE_KEY)
      return empty
    }

    if (parsed.expiresAt <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY)
      return empty
    }

    return { token: parsed.token, expiresAt: parsed.expiresAt, user: parsed.user }
  } catch {
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // 存储不可用时静默降级为内存会话
    }
    return empty
  }
}

const state = reactive<SessionState>(readStoredSession())

export const sessionUser = computed<UserVO | null>(() => state.user)
export const isLoggedIn = computed(() => Boolean(state.token) && state.expiresAt > Date.now())
export const isAdmin = computed(() => state.user?.role === 'ADMIN')

export function getToken(): string {
  if (!state.token) return ''
  if (state.expiresAt <= Date.now()) {
    clearSession()
    return ''
  }
  return state.token
}

export function saveSession(payload: LoginResponse): void {
  const ttlSeconds =
    typeof payload.expiresIn === 'number' && payload.expiresIn > 0
      ? payload.expiresIn
      : DEFAULT_TTL_SECONDS
  const role: UserRole = String(payload.user.role).toUpperCase() === 'ADMIN' ? 'ADMIN' : 'USER'
  const user: UserVO = { ...payload.user, role }
  const expiresAt = Date.now() + ttlSeconds * 1000

  state.token = payload.token
  state.user = user
  state.expiresAt = expiresAt

  const stored: StoredSession = {
    version: STORAGE_VERSION,
    token: payload.token,
    expiresAt,
    user
  }

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored))
  } catch {
    // 极少数隐私模式下 localStorage 不可写；此时会话只在当前内存中有效
  }
}

export function clearSession(): void {
  state.token = ''
  state.user = null
  state.expiresAt = 0
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 忽略存储异常
  }
}

/**
 * 第 6 周 session.ts 的注释是“仅存内存、不要改成 sessionStorage 假装持久化”，
 * 前提是当时后端不签发令牌。自第 9 周起后端签发带有效期的 Session Token（决策 D3），
 * 前端把令牌持久化到 localStorage 才成立：刷新页面不掉登录，登出或令牌过期即清除。
 * 这不是“假装持久化”，而是令牌机制落地后的正常做法。
 */
