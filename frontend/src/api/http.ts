import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { getToken, clearToken } from '@/store/session'

/**
 * HTTP 客户端封装（第 9 周）。
 *
 * <p>做四件事，避免每个页面各写一套：
 * <ol>
 *   <li>统一 baseURL —— 直连后端 8080 端口（跨域由后端 CorsConfig 放行）；</li>
 *   <li>拆掉 {@link ApiResponse} 外壳，业务代码只拿到 data；</li>
 *   <li>把网络层异常翻译成能直接给人看的中文提示（NF-10）；</li>
 *   <li><b>带上会话令牌</b> —— 第 9 周起后端开始签发令牌，业务请求一律带
 *       {@code Authorization: Bearer <token>}（FR-01、NF-04）。</li>
 * </ol>
 *
 * <p>注意后端约定：业务失败也返回 HTTP 200，靠 body 里的 {@code code} 区分结果，
 * 因此这里判的是 code 而不是 HTTP 状态码。</p>
 */
const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

const http = axios.create({
  baseURL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 请求拦截器：自动带上令牌。
 *
 * <p>★ 令牌过期时后端返回 401，这里统一清掉本地登录态并跳回登录页——
 * 否则页面会一直拿着一个失效令牌发请求，用户看到的只是一连串"未登录"报错，
 * 不知道该重新登录（NF-04「令牌过期后自动强制退出系统」）。</p>
 */
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 把 axios 的各种异常翻译成一句能直接展示的中文 */
function toFriendlyMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') {
      return '请求超时：后端响应超过 10 秒'
    }
    if (!error.response) {
      return `无法连接后端（${baseURL}），请确认后端已启动、端口未被占用`
    }
    const body = error.response.data as ApiResponse<unknown> | undefined
    if (body && typeof body.message === 'string' && body.message) {
      return body.message
    }
    return `请求失败：HTTP ${error.response.status}`
  }
  return error instanceof Error ? error.message : '未知错误'
}

/**
 * 业务错误（第 9 周新增）。
 *
 * <p>★ 为什么需要它：后端 409 冲突响应会在 {@code data.occupiedSlots} 里返回
 * "被占用的时段"（FR-10 硬要求），而普通 {@link Error} 只能带一句 message，
 * 拿不到这个 data——那前端就<b>做不出"展示被占用时段"</b>。
 * 因此这里把 code 与 data 一并挂到 Error 上，业务代码就能取到。</p>
 *
 * <p>用法：{@code catch (e) { if (e instanceof ApiBusinessError && e.code === 409) { e.data.occupiedSlots } }}</p>
 */
export class ApiBusinessError extends Error {
  /** 业务码：400／401／403／404／409 */
  readonly code: number
  /** 后端返回的业务数据；可能是 null */
  readonly data: unknown

  constructor(message: string, code: number, data: unknown) {
    super(message)
    this.name = 'ApiBusinessError'
    this.code = code
    this.data = data
  }
}

/** 统一的请求入口：返回拆壳后的业务数据，失败时抛 ApiBusinessError(中文提示) */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiResponse<T>>(config)
    const body = response.data
    if (!body || typeof body.code !== 'number') {
      throw new Error('后端返回格式不符合约定（缺少 code 字段）')
    }
    if (body.code !== 200) {
      // ★ 业务失败：带上 code 与 data（409 要用 data.occupiedSlots）
      throw new ApiBusinessError(body.message || '请求失败', body.code, body.data)
    }
    return body.data
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      // 401：令牌失效或未登录 → 清登录态，交给页面跳登录页
      if (error.code === 401) {
        clearToken()
      }
      throw error
    }
    throw new ApiBusinessError(toFriendlyMessage(error), 500, null)
  }
}

export { baseURL }
