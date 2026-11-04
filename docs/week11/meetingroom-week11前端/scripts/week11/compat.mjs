import { spawn, spawnSync } from 'node:child_process'
import crypto from 'node:crypto'
import { access, mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { chromium } from 'playwright-core'
import {
  ROOT,
  SHOT_DIR,
  addDays,
  createChecks,
  nowLocalString,
  startMock,
  stopChild,
  toDateString,
  tomorrowString,
  waitForHttp,
  writeJson,
  writeText
} from './lib.mjs'

/**
 * 第 11 周适配类测试（孙若普）：
 * - TC-09：1366×768、375×667、768×1024 三种视口，列表页/详情页无横向溢出
 * - TC-11：Edge 全流程（登录→查询→预约→取消）；Chrome 已安装时同样执行
 *
 * 不下载浏览器，只用本机已安装的 Edge / Chrome。
 */

const MOCK_PORT = 28080
const WEB_PORT = 5399
const tomorrow = tomorrowString()
const checks = createChecks()
const startedAt = nowLocalString()

const USER_PASSWORD = crypto.randomBytes(12).toString('base64url')
const ADMIN_PASSWORD = crypto.randomBytes(12).toString('base64url')

const BROWSER_CANDIDATES = [
  {
    name: 'Edge',
    paths: [
      'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
      'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
    ]
  },
  {
    name: 'Chrome',
    paths: [
      'C:/Program Files/Google/Chrome/Application/chrome.exe',
      'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
      path.join(process.env.LOCALAPPDATA || '', 'Google/Chrome/Application/chrome.exe')
    ]
  }
]

async function exists(filePath) {
  try {
    await access(filePath)
    return true
  } catch {
    return false
  }
}

function fileVersion(exePath) {
  const script = `(Get-Item -LiteralPath '${exePath.replaceAll("'", "''")}').VersionInfo.FileVersion`
  const result = spawnSync('powershell', ['-NoProfile', '-Command', script], { encoding: 'utf8' })
  return (result.stdout || '').trim() || 'unknown'
}

async function detectBrowsers() {
  const result = []
  for (const candidate of BROWSER_CANDIDATES) {
    let found = ''
    for (const filePath of candidate.paths) {
      if (await exists(filePath)) {
        found = filePath
        break
      }
    }
    result.push({
      name: candidate.name,
      available: Boolean(found),
      executablePath: found,
      version: found ? fileVersion(found) : '',
      reason: found ? '' : '本机未安装'
    })
  }
  return result
}

async function screenshot(page, name) {
  const target = path.join(SHOT_DIR, `${name}.png`)
  await page.screenshot({ path: target, fullPage: true })
  return target
}

async function measureOverflow(page) {
  return page.evaluate(() => ({
    innerWidth: window.innerWidth,
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth
  }))
}

function noOverflow(metrics) {
  return metrics.documentScrollWidth <= metrics.innerWidth + 1
}

async function uiLogin(page, baseUrl, password) {
  await page.goto(`${baseUrl}/login`)
  await page.waitForSelector('#login-username')
  await page.fill('#login-username', 'user001')
  await page.fill('#login-password', password)
  const loginResponse = page.waitForResponse(
    (response) => response.url().includes('/api/auth/login') && response.request().method() === 'POST'
  )
  await page.click('button[type="submit"]')
  const response = await loginResponse
  if (response.status() !== 200) {
    throw new Error(`UI 登录失败：HTTP ${response.status()}`)
  }
  await page.waitForURL(/\/rooms$/)
  await page.waitForSelector('table.table tbody tr')
}

async function waitLoadingDone(page) {
  await page.waitForFunction(() => !document.querySelector('.loading'), null, { timeout: 15_000 })
}

async function runViewport(browser, browserInfo, baseUrl, viewport) {
  const context = await browser.newContext({
    viewport,
    locale: 'zh-CN',
    ...(viewport.width <= 768 ? { isMobile: true, hasTouch: true } : {})
  })
  const page = await context.newPage()
  const consoleErrors = []
  const failedRequests = []

  page.on('console', (message) => {
    if (message.type() !== 'error') return
    const text = message.text()
    if (text.includes('Failed to load resource') && /status of (400|401|403|409)/.test(text)) return
    consoleErrors.push(text)
  })
  page.on('pageerror', (error) => consoleErrors.push(String(error)))
  page.on('requestfailed', (request) => {
    const failure = request.failure()?.errorText || 'unknown'
    // 导航被新页面替换时 Chromium 会报 ERR_ABORTED，不算网络失败
    if (failure.includes('net::ERR_ABORTED')) return
    failedRequests.push(`${request.method()} ${request.url()} ${failure}`)
  })

  try {
    await uiLogin(page, baseUrl, USER_PASSWORD)
    await waitLoadingDone(page)

    const listMetrics = await measureOverflow(page)
    const queryButtonEnabled = await page.getByRole('button', { name: '查询' }).isEnabled()
    const detailLinkVisible = await page.getByRole('link', { name: '进详情' }).first().isVisible()
    const listShot = await screenshot(page, `${browserInfo.name}_${viewport.width}x${viewport.height}_列表`)

    await page.getByRole('link', { name: '进详情' }).first().click()
    await page.waitForURL(/\/rooms\/\d+/)
    await waitLoadingDone(page)
    const detailMetrics = await measureOverflow(page)
    const detailShot = await screenshot(page, `${browserInfo.name}_${viewport.width}x${viewport.height}_详情`)

    const pass =
      noOverflow(listMetrics) &&
      noOverflow(detailMetrics) &&
      queryButtonEnabled &&
      detailLinkVisible &&
      consoleErrors.length === 0 &&
      failedRequests.length === 0
    checks.check(
      `TC-09-${browserInfo.name}-${viewport.width}x${viewport.height}`,
      `适配 ${viewport.width}×${viewport.height} 列表页与详情页无横向溢出`,
      pass,
      `列表 scrollWidth=${listMetrics.documentScrollWidth}/${listMetrics.innerWidth}；` +
      `详情 scrollWidth=${detailMetrics.documentScrollWidth}/${detailMetrics.innerWidth}；` +
      `查询按钮=${queryButtonEnabled ? '可点' : '不可点'}；控制台错误=${consoleErrors.length}；网络失败=${failedRequests.length}`
    )

    return {
      width: viewport.width,
      height: viewport.height,
      pass,
      list: listMetrics,
      detail: detailMetrics,
      queryButtonEnabled,
      detailLinkVisible,
      screenshots: [listShot, detailShot],
      consoleErrors,
      failedRequests
    }
  } finally {
    await context.close()
  }
}

async function runFlow(browser, browserInfo, baseUrl) {
  const context = await browser.newContext({
    viewport: { width: 1366, height: 768 },
    locale: 'zh-CN'
  })
  const page = await context.newPage()
  const consoleErrors = []
  const failedRequests = []
  const steps = []

  page.on('console', (message) => {
    if (message.type() !== 'error') return
    const text = message.text()
    if (text.includes('Failed to load resource') && /status of (400|401|403|409)/.test(text)) return
    consoleErrors.push(text)
  })
  page.on('pageerror', (error) => consoleErrors.push(String(error)))
  page.on('requestfailed', (request) => {
    const failure = request.failure()?.errorText || 'unknown'
    if (failure.includes('net::ERR_ABORTED')) return
    failedRequests.push(`${request.method()} ${request.url()} ${failure}`)
  })

  try {
    await uiLogin(page, baseUrl, USER_PASSWORD)
    steps.push('登录成功')

    await page.fill('#filter-name', '301')
    const filterResponse = page.waitForResponse(
      (response) => response.url().includes('/api/rooms?') && response.request().method() === 'GET'
    )
    await page.getByRole('button', { name: '查询' }).click()
    await filterResponse
    await waitLoadingDone(page)
    const rowCount = await page.locator('table.table tbody tr').count()
    const firstRowText = await page.locator('table.table tbody tr').first().innerText()
    if (rowCount !== 1 || !firstRowText.includes('3号楼301')) {
      throw new Error(`筛选结果异常：rows=${rowCount} text=${firstRowText}`)
    }
    steps.push('按名称筛选 301 命中 1 条')

    await page.getByRole('button', { name: '重置' }).click()
    await waitLoadingDone(page)

    await page.goto(`${baseUrl}/bookings/new?roomId=1&date=${tomorrow}&start=11:00&end=12:00`)
    await page.waitForSelector('#booking-subject')
    await page.fill('#booking-subject', 'W11-兼容性-全流程')
    await page.fill('#booking-attendees', '8')
    await page.fill('#booking-contact', '张同学')

    const createResponse = page.waitForResponse(
      (response) => response.url().endsWith('/api/bookings') && response.request().method() === 'POST'
    )
    await page.getByRole('button', { name: '提交预约' }).click()
    const created = await createResponse
    if (created.status() !== 200) {
      throw new Error(`提交预约失败：HTTP ${created.status()}`)
    }
    await page.waitForSelector('.success-panel')
    const successText = await page.locator('.success-panel').innerText()
    if (!successText.includes('预约成功') || !/MR\d{8,}/.test(successText)) {
      throw new Error(`预约成功页缺少预约编号：${successText}`)
    }
    const bookingNo = /MR\d{8,}/.exec(successText)?.[0] || ''
    steps.push(`提交预约成功（${bookingNo}）`)
    const successShot = await screenshot(page, `${browserInfo.name}_全流程_预约成功`)

    await page.goto(`${baseUrl}/bookings/mine`)
    await page.waitForSelector('table.table tbody tr')
    const row = page.locator('table.table tbody tr', { hasText: 'W11-兼容性-全流程' }).first()
    if (!(await row.isVisible())) {
      throw new Error('我的预约中未找到刚提交的记录')
    }

    const cancelResponse = page.waitForResponse(
      (response) => /\/api\/bookings\/\d+$/.test(new URL(response.url()).pathname) && response.request().method() === 'DELETE'
    )
    await row.getByRole('button', { name: '取消', exact: true }).click()
    await page.waitForSelector('.modal')
    await page.getByRole('button', { name: '确认取消预约' }).click()
    const cancelled = await cancelResponse
    if (cancelled.status() !== 200) {
      throw new Error(`取消预约失败：HTTP ${cancelled.status()}`)
    }
    await page.waitForFunction(() => document.body.innerText.includes('已取消'), null, { timeout: 15_000 })
    steps.push('取消预约成功，状态变为已取消')
    const cancelShot = await screenshot(page, `${browserInfo.name}_全流程_取消成功`)

    const pass = consoleErrors.length === 0 && failedRequests.length === 0
    checks.check(
      `TC-11-${browserInfo.name}`,
      `${browserInfo.name} 全流程：登录→查询→预约→取消`,
      pass,
      pass
        ? steps.join('；')
        : `控制台错误 ${consoleErrors.length} 条；网络失败 ${failedRequests.length} 条`
    )

    return {
      pass,
      steps,
      bookingNo,
      screenshots: [successShot, cancelShot],
      consoleErrors,
      failedRequests
    }
  } finally {
    await context.close()
  }
}

async function main() {
  await mkdir(SHOT_DIR, { recursive: true })
  const detected = await detectBrowsers()
  const warnings = detected
    .filter((item) => !item.available)
    .map((item) => `${item.name} 未安装，相关用例未执行`)

  const mock = await startMock({
    port: MOCK_PORT,
    userPassword: USER_PASSWORD,
    adminPassword: ADMIN_PASSWORD,
    logName: 'week11-compat-mock.log'
  })

  const webLogs = []
  const web = spawn(
    process.execPath,
    [
      path.join(ROOT, 'frontend', 'node_modules', 'vite', 'bin', 'vite.js'),
      '--host',
      '127.0.0.1',
      '--port',
      String(WEB_PORT),
      '--strictPort'
    ],
    {
      cwd: path.join(ROOT, 'frontend'),
      env: {
        ...process.env,
        VITE_PROXY_TARGET: `http://localhost:${MOCK_PORT}`
      },
      stdio: ['ignore', 'pipe', 'pipe']
    }
  )
  web.stdout.on('data', (chunk) => webLogs.push(`[stdout] ${chunk}`))
  web.stderr.on('data', (chunk) => webLogs.push(`[stderr] ${chunk}`))

  const baseUrl = `http://127.0.0.1:${WEB_PORT}`
  const browserResults = []

  try {
    await waitForHttp(`http://localhost:${MOCK_PORT}/api/health`)
    await waitForHttp(baseUrl)

    for (const browserInfo of detected) {
      if (!browserInfo.available) {
        browserResults.push({ ...browserInfo, viewports: [], flow: null })
        continue
      }

      const browser = await chromium.launch({
        executablePath: browserInfo.executablePath,
        headless: true
      })
      try {
        const viewports = []
        for (const viewport of [
          { width: 1366, height: 768 },
          { width: 375, height: 667 },
          { width: 768, height: 1024 }
        ]) {
          viewports.push(await runViewport(browser, browserInfo, baseUrl, viewport))
        }
        const flow = await runFlow(browser, browserInfo, baseUrl)
        browserResults.push({ ...browserInfo, viewports, flow })
      } finally {
        await browser.close()
      }
    }

    const summary = checks.summary()
    const available = browserResults.filter((item) => item.available)
    const overallPass = available.length > 0 && available.every((item) =>
      item.viewports.every((viewport) => viewport.pass) &&
      item.flow?.pass &&
      (item.consoleErrors || []).length === 0
    )

    await writeJson(path.join('docs', 'week11', '适配截图', 'compat-result.json'), {
      executedAt: startedAt,
      finishedAt: nowLocalString(),
      webUrl: baseUrl,
      mockUrl: `http://localhost:${MOCK_PORT}`,
      browsers: browserResults,
      warnings,
      checks: checks.items,
      summary,
      overallPass
    })

    console.log('')
    console.log(`适配检查汇总：${summary.passed}/${summary.total} 通过，${summary.failed} 失败`)
    if (warnings.length > 0) console.log(`环境提示：${warnings.join('；')}`)
    if (summary.failed > 0 || !overallPass) process.exitCode = 1
  } finally {
    await stopChild(web)
    await writeText(path.join('docs', 'week11', '验证日志', 'week11-compat-web.log'), webLogs.join(''))
    await mock.stop()
  }
}

main().catch((error) => {
  console.error(`[FATAL] ${error.message}`)
  process.exitCode = 1
})
