import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
export const EVIDENCE_DIR = path.join(ROOT, 'docs', 'week11', '取证')
export const SHOT_DIR = path.join(ROOT, 'docs', 'week11', '适配截图')
export const LOG_DIR = path.join(ROOT, 'docs', 'week11', '验证日志')

export function pad2(value) {
  return String(value).padStart(2, '0')
}

export function toDateString(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

export function nowLocalString() {
  const date = new Date()
  return `${toDateString(date)} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

export function tomorrowString() {
  return toDateString(addDays(new Date(), 1))
}

export function dayAfterTomorrowString() {
  return toDateString(addDays(new Date(), 2))
}

/** 破坏性测试只允许本机地址，防止误伤学校在用系统或公网服务。 */
export function assertLocalBaseUrl(baseUrl) {
  const url = new URL(baseUrl)
  const allowed = new Set(['localhost', '127.0.0.1', '::1', '[::1]'])
  if (!allowed.has(url.hostname)) {
    throw new Error(`安全边界：破坏性测试只允许 localhost / 127.0.0.1，当前为 ${url.hostname}`)
  }
  return url
}

function redactToken(token) {
  if (!token) return ''
  const preview = token.slice(0, 8)
  return `${preview}…（已脱敏，共 ${token.length} 字符）`
}

export async function apiRequest(baseUrl, {
  method = 'GET',
  apiPath,
  token = '',
  body = null
}) {
  const url = `${baseUrl}${apiPath}`
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== null) headers['Content-Type'] = 'application/json'

  const startedAt = Date.now()
  const response = await fetch(url, {
    method,
    headers,
    ...(body !== null ? { body: JSON.stringify(body) } : {})
  })
  const durationMs = Date.now() - startedAt
  const text = await response.text()
  let parsed = text
  try {
    parsed = text ? JSON.parse(text) : null
  } catch {
    // 非 JSON 响应原样保留
  }

  const evidenceHeaders = {}
  for (const [key, value] of Object.entries(headers)) {
    evidenceHeaders[key] = key.toLowerCase() === 'authorization'
      ? `Bearer ${redactToken(token)}`
      : value
  }

  return {
    at: nowLocalString(),
    method,
    url,
    requestHeaders: evidenceHeaders,
    requestBody: body,
    status: response.status,
    responseHeaders: Object.fromEntries(response.headers.entries()),
    responseBody: parsed,
    durationMs
  }
}

function indent(text, prefix) {
  return text.split('\n').map((line) => `${prefix}${line}`).join('\n')
}

export function renderRequest(entry) {
  const headerText = Object.entries(entry.requestHeaders)
    .map(([key, value]) => `  ${key}: ${value}`)
    .join('\n')
  const bodyText = entry.requestBody === null
    ? '  （无）'
    : indent(JSON.stringify(entry.requestBody, null, 2), '  ')
  return [
    `时间：${entry.at}`,
    `方法：${entry.method}`,
    `URL：${entry.url}`,
    '请求头：',
    headerText,
    '请求体：',
    bodyText,
    ''
  ].join('\n')
}

export function renderResponse(entry) {
  const bodyText = typeof entry.responseBody === 'string'
    ? entry.responseBody
    : JSON.stringify(entry.responseBody, null, 2)
  return [
    `HTTP 状态码：${entry.status}`,
    `耗时：${entry.durationMs} ms`,
    '响应头（节选）：',
    `  Content-Type: ${entry.responseHeaders['content-type'] || '-'}`,
    '响应体：',
    indent(bodyText || '（空）', '  '),
    ''
  ].join('\n')
}

export async function writeText(relativePath, content) {
  const target = path.join(ROOT, relativePath)
  await mkdir(path.dirname(target), { recursive: true })
  await writeFile(target, content, 'utf8')
  return target
}

export async function writeJson(relativePath, data) {
  return writeText(relativePath, `${JSON.stringify(data, null, 2)}\n`)
}

export async function login(baseUrl, username, password) {
  const response = await apiRequest(baseUrl, {
    method: 'POST',
    apiPath: '/api/auth/login',
    body: { username, password }
  })
  if (response.status !== 200 || !response.responseBody?.data?.token) {
    throw new Error(`登录失败：${username} status=${response.status} message=${response.responseBody?.message || '-'}`)
  }
  return {
    token: response.responseBody.data.token,
    user: response.responseBody.data.user,
    raw: response
  }
}

export function createChecks() {
  const items = []
  return {
    items,
    check(id, name, ok, detail = '') {
      items.push({ id, name, ok: Boolean(ok), detail })
      console.log(`[${ok ? 'PASS' : 'FAIL'}] ${id} ${name}${detail ? `：${detail}` : ''}`)
      return Boolean(ok)
    },
    summary() {
      const failed = items.filter((item) => !item.ok)
      return {
        total: items.length,
        passed: items.length - failed.length,
        failed: failed.length
      }
    }
  }
}

export async function isHealthy(baseUrl) {
  try {
    const response = await fetch(`${baseUrl}/api/health`)
    return response.ok
  } catch {
    return false
  }
}

export async function waitForHttp(url, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {
      // 服务尚未就绪
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`等待服务超时：${url}`)
}

/** 只启动/关闭本脚本自己拉起的 mock 子进程。 */
export async function startMock({ port, userPassword, adminPassword, logName = 'mock.log' }) {
  const child = spawn(process.execPath, ['mock-server/server.mjs'], {
    cwd: ROOT,
    env: {
      ...process.env,
      MOCK_PORT: String(port),
      MOCK_TOKEN_TTL_SECONDS: '1800',
      MOCK_USER_PASSWORD: userPassword,
      MOCK_ADMIN_PASSWORD: adminPassword,
      MOCK_QUIET_CREDENTIALS: '1'
    },
    stdio: ['ignore', 'pipe', 'pipe']
  })

  const logs = []
  child.stdout.on('data', (chunk) => logs.push(`[stdout] ${chunk}`))
  child.stderr.on('data', (chunk) => logs.push(`[stderr] ${chunk}`))

  try {
    await waitForHttp(`http://localhost:${port}/api/health`)
  } catch (error) {
    await stopChild(child)
    const detail = logs.join('')
    throw new Error(`${error.message}\n${detail}`)
  }

  return {
    child,
    logs,
    async stop() {
      await stopChild(child)
      await writeText(path.join('docs', 'week11', '验证日志', logName), logs.join(''))
    }
  }
}

/** 只结束本脚本创建的 child，不使用 taskkill / Stop-Process。 */
export async function stopChild(child) {
  if (!child || child.exitCode !== null) return
  child.kill('SIGTERM')
  await new Promise((resolve) => {
    const timer = setTimeout(resolve, 3000)
    child.once('exit', () => {
      clearTimeout(timer)
      resolve()
    })
  })
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
