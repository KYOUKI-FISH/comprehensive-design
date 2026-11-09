import { clearSession, getToken } from '@/session/session'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')
const TIMEOUT_MS = 15_000

interface ApiEnvelope<T> {
  code?: number
  message?: string
  data?: T
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /** 是否携带令牌；登录请求传 false */
  auth?: boolean
}

/**
 * 统一的接口异常。data 字段是第 9 周必须补上的能力：
 * 预约冲突时后端 409 的 data.occupiedSlots 要能传到页面（FR-10）。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code?: number
  readonly data: unknown

  constructor(message: string, status: number, code?: number, data?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.data = data
  }
}

function fallbackMessage(status: number): string {
  switch (status) {
    case 400:
      return '提交的信息不正确，请检查后重试'
    case 401:
      return '登录已过期，请重新登录'
    case 403:
      return '当前账号无权执行该操作'
    case 404:
      return '请求的资源不存在'
    case 409:
      return '请求与当前数据冲突'
    case 500:
      return '服务器开小差了，请稍后重试'
    default:
      return '请求失败，请稍后重试'
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options
  const headers: Record<string, string> = { Accept: 'application/json' }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  if (auth) {
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('请求超时，请稍后重试', 0)
    }
    throw new ApiError('网络异常，请检查网络或后端服务是否已启动', 0)
  } finally {
    clearTimeout(timer)
  }

  const text = await response.text()
  let payload: ApiEnvelope<T> | null = null
  if (text) {
    try {
      payload = JSON.parse(text) as ApiEnvelope<T>
    } catch {
      payload = null
    }
  }

  if (!response.ok) {
    const message = payload?.message || fallbackMessage(response.status)

    if (response.status === 401 && auth) {
      clearSession()
      window.dispatchEvent(new CustomEvent('auth:expired'))
    }

    throw new ApiError(message, response.status, payload?.code, payload?.data)
  }

  if (payload && typeof payload === 'object' && 'data' in payload) {
    return payload.data as T
  }

  return payload as T
}

export const http = {
  get<T>(path: string): Promise<T> {
    return request<T>(path)
  },
  post<T>(path: string, body?: unknown, auth = true): Promise<T> {
    return request<T>(path, { method: 'POST', body, auth })
  },
  put<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, { method: 'PUT', body })
  },
  delete<T>(path: string): Promise<T> {
    return request<T>(path, { method: 'DELETE' })
  }
}
