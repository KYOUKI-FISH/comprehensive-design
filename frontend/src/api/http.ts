import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResult } from '@/types/api'

/**
 * HTTP 客户端封装（骨架级）。
 *
 * <p>做三件事，避免每个页面各写一套：
 * <ol>
 *   <li>统一 baseURL —— 直连后端 8080 端口（跨域由后端 CORS 放行）；</li>
 *   <li>拆掉 {@link ApiResult} 外壳，业务代码只拿到 data；</li>
 *   <li>把网络层异常翻译成能直接给人看的中文提示（联调时省事）。</li>
 * </ol>
 */
const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

const http = axios.create({
  baseURL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
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
    const body = error.response.data as ApiResult<unknown> | undefined
    if (body && typeof body.message === 'string' && body.message) {
      return body.message
    }
    return `请求失败：HTTP ${error.response.status}`
  }
  return error instanceof Error ? error.message : '未知错误'
}

/** 统一的请求入口：返回拆壳后的业务数据，失败时抛 Error(中文提示) */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiResult<T>>(config)
    const body = response.data
    if (!body || typeof body.code !== 'number') {
      throw new Error('后端返回格式不符合约定（缺少 code 字段）')
    }
    if (body.code !== 200) {
      throw new Error(body.message || '请求失败')
    }
    return body.data
  } catch (error) {
    throw new Error(toFriendlyMessage(error))
  }
}

export { baseURL }
