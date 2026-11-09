import { ApiError } from '@/api/http'

/**
 * NF-10：页面只展示中文业务提示。
 * 后端返回的 message 优先；只有消息缺失或明显是技术信息时，才退到状态码兜底文案。
 */
const STATUS_FALLBACKS: Record<number, string> = {
  0: '网络异常，请检查网络或后端服务是否已启动',
  400: '提交的信息不正确，请检查后重试',
  401: '登录已过期，请重新登录',
  403: '当前账号无权执行该操作',
  404: '请求的数据不存在',
  405: '当前操作不被允许',
  409: '请求与当前数据冲突',
  413: '提交的内容过大',
  500: '服务器开小差了，请稍后重试'
}

function looksTechnical(message: string): boolean {
  return (
    /(^|\D)(4\d{2}|5\d{2})(\D|$)/.test(message) ||
    /(^|\s)(TypeError|ReferenceError|SyntaxError|RangeError|Error)\s*:/.test(message) ||
    message.startsWith('Error:') ||
    message.includes('stack')
  )
}

export function toUserMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  if (error instanceof ApiError) {
    const message = (error.message || '').trim()
    if (message && !looksTechnical(message)) return message
    return STATUS_FALLBACKS[error.status] ?? fallback
  }

  if (error instanceof Error) {
    const message = (error.message || '').trim()
    if (message && !looksTechnical(message)) return message
  }

  return fallback
}

export function isHttpStatus(error: unknown, status: number): boolean {
  return error instanceof ApiError && error.status === status
}

export function isUnauthorized(error: unknown): boolean {
  return isHttpStatus(error, 401)
}
