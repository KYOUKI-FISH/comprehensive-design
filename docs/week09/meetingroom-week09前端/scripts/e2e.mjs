import { spawn } from 'node:child_process'
import crypto from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

/**
 * 第 9 周前端端到端验证（开发自测工具，不属于第 11 周正式测试材料）。
 * - 自己拉起 mock 后端与 Vite，验证完自行关闭
 * - 使用本机 Edge（playwright-core，不下载浏览器）
 * - 截图输出到 docs/week09/自测截图/
 */

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const EDGE_PATH = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const MOCK_PORT = 18080
const WEB_PORT = 5199
const SCREENSHOT_DIR = path.join(ROOT, 'docs', 'week09', '自测截图')
// 每次运行随机生成演示口令，仓库里不出现可用口令
const USER_PASSWORD = crypto.randomBytes(12).toString('base64url')
const ADMIN_PASSWORD = crypto.randomBytes(12).toString('base64url')

function pad2(value) {
  return String(value).padStart(2, '0')
}

function toDateString(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

const tomorrow = toDateString(addDays(new Date(), 1))
const dayAfterTomorrow = toDateString(addDays(new Date(), 2))

const checks = []
const consoleErrors = []
const failedRequests = []

function pass(name, detail = '') {
  checks.push({ name, ok: true, detail })
  console.log(`[PASS] ${name}${detail ? `：${detail}` : ''}`)
}

function fail(name, detail = '') {
  checks.push({ name, ok: false, detail })
  console.log(`[FAIL] ${name}${detail ? `：${detail}` : ''}`)
}

function assert(condition, name, detail = '') {
  if (condition) pass(name, detail)
  else fail(name, detail)
  return condition
}

/** 只关闭本脚本自己拉起的子进程，不触碰任何外部进程。 */
async function stopChild(child) {
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

async function waitForHttp(url, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {
      // 服务还没起来，继续等
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`等待服务超时：${url}`)
}

async function waitForRowCount(page, expected) {
  try {
    await page.waitForFunction(
      (count) => document.querySelectorAll('table.table tbody tr').length === count,
      expected,
      { timeout: 10_000 }
    )
  } catch (error) {
    const rows = await page.locator('table.table tbody tr').count()
    const head = await page.locator('.table-card__head').innerText().catch(() => '')
    const loading = await page.locator('.loading').count()
    const filters = await page.evaluate(() => ({
      name: document.querySelector('#filter-name')?.value,
      capacity: document.querySelector('#filter-capacity')?.value,
      location: document.querySelector('#filter-location')?.value,
      errors: Array.from(document.querySelectorAll('.field-error')).map((node) => node.textContent),
      submitDisabled: document.querySelector('form.filters button[type="submit"]')?.disabled
    }))
    console.error(
      `[debug] 期望 ${expected} 行，实际 ${rows} 行；loading=${loading}；表头=${head.replace(/\n/g, ' / ')}；筛选=${JSON.stringify(filters)}`
    )
    throw error
  }
}

async function screenshot(page, name) {
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, `${name}.png`),
    fullPage: true
  })
}

async function main() {
  await mkdir(SCREENSHOT_DIR, { recursive: true })

  const mock = spawn(process.execPath, ['mock-server/server.mjs'], {
    cwd: ROOT,
    env: {
      ...process.env,
      MOCK_PORT: String(MOCK_PORT),
      MOCK_TOKEN_TTL_SECONDS: '1800',
      MOCK_USER_PASSWORD: USER_PASSWORD,
      MOCK_ADMIN_PASSWORD: ADMIN_PASSWORD,
      MOCK_QUIET_CREDENTIALS: '1'
    },
    stdio: ['ignore', 'pipe', 'pipe']
  })
  mock.stdout.on('data', (chunk) => process.stdout.write(`[mock] ${chunk}`))
  mock.stderr.on('data', (chunk) => process.stderr.write(`[mock] ${chunk}`))

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
  web.stdout.on('data', (chunk) => process.stdout.write(`[web] ${chunk}`))
  web.stderr.on('data', (chunk) => process.stderr.write(`[web] ${chunk}`))

  const baseUrl = `http://127.0.0.1:${WEB_PORT}`
  await waitForHttp(`http://localhost:${MOCK_PORT}/api/health`)
  await waitForHttp(baseUrl)

  const browser = await chromium.launch({
    executablePath: EDGE_PATH,
    headless: true
  })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN'
  })
  const page = await context.newPage()

  page.on('console', (message) => {
    if (message.type() !== 'error') return
    const text = message.text()
    // 业务失败（登录 401、冲突 409 等）浏览器会记一条资源错误，属预期；
    // 只保留真正的脚本错误和非预期资源错误。
    if (text.includes('Failed to load resource') && /status of (400|401|403|409)/.test(text)) {
      return
    }
    consoleErrors.push(text)
  })
  page.on('pageerror', (error) => consoleErrors.push(String(error)))
  page.on('requestfailed', (request) => {
    failedRequests.push(`${request.method()} ${request.url()} ${request.failure()?.errorText || ''}`)
  })

  try {
    // ---------- 1. 未登录访问受限页 → 登录页 ----------
    await page.goto(`${baseUrl}/rooms`)
    await page.waitForURL(/\/login/)
    assert(
      page.url().includes('/login?redirect=/rooms'),
      '未登录访问会议室列表被重定向登录页并带 redirect'
    )
    assert(
      (await page.locator('.login-title').innerText()).includes('智能会议室管理系统'),
      '登录页显示系统名称'
    )
    assert(
      (await page.locator('.login-card a').count()) === 0,
      '登录页没有“注册/忘记密码”入口'
    )
    await screenshot(page, '01_登录页')

    // ---------- 2. 错误口令 → 同一句提示 ----------
    await page.fill('#login-username', 'user001')
    await page.fill('#login-password', 'wrong-password')
    await page.click('button[type="submit"]')
    await page.waitForSelector('.alert--error')
    const wrongPasswordMessage = (await page.locator('.alert--error').innerText()).trim()
    assert(
      wrongPasswordMessage.includes('账号或密码错误'),
      '错误口令显示中文业务提示',
      wrongPasswordMessage
    )

    // ---------- 3. 普通用户登录 ----------
    await page.fill('#login-password', USER_PASSWORD)
    await page.click('button[type="submit"]')
    await page.waitForURL(/\/rooms$/)
    await page.waitForSelector('table.table tbody tr')
    assert(page.url().endsWith('/rooms'), '普通用户登录后进入会议室列表')
    assert(
      (await page.locator('table.table tbody tr').count()) === 6,
      '会议室列表显示后端返回的 6 间会议室（非前端假数据）'
    )
    const userNavText = await page.locator('nav.nav').innerText()
    assert(userNavText.includes('会议室查询'), '普通用户菜单包含“会议室查询”')
    assert(userNavText.includes('我的预约'), '普通用户菜单包含“我的预约”')
    assert(
      !userNavText.includes('用户管理') &&
        !userNavText.includes('会议室管理') &&
        !userNavText.includes('全部预约处理') &&
        !userNavText.includes('操作日志'),
      '普通用户菜单不显示任何管理入口（FR-02）',
      userNavText.replace(/\n/g, ' / ')
    )
    assert(
      (await page.locator('table.table tbody tr', { hasText: '已停用' }).count()) === 1,
      '列表正确显示已停用会议室状态（FR-06）'
    )
    await screenshot(page, '02_会议室列表_普通用户')

    // ---------- 4. 多条件筛选 ----------
    await page.fill('#filter-name', '301')
    await page.click('form.filters button[type="submit"]')
    await waitForRowCount(page, 1)
    assert(
      (await page.locator('table.table tbody tr').innerText()).includes('3号楼301'),
      '按名称筛选命中 3号楼301（FR-05）'
    )

    await page.click('form.filters button[type="reset"], form.filters button:has-text("重置")')
    await waitForRowCount(page, 6)
    await page.fill('#filter-capacity', '100')
    await page.click('form.filters button[type="submit"]')
    await waitForRowCount(page, 1)
    assert(
      (await page.locator('table.table tbody tr').innerText()).includes('学术报告厅'),
      '按最小容量 100 人筛选命中 120 人报告厅（FR-05）'
    )

    await page.click('form.filters button:has-text("重置")')
    await waitForRowCount(page, 6)
    await page.fill('#filter-location', '3号楼')
    await page.click('form.filters button[type="submit"]')
    await waitForRowCount(page, 1)
    assert(
      (await page.locator('table.table tbody tr').innerText()).includes('3号楼301'),
      '按位置筛选命中 3号楼301（FR-05）'
    )

    await page.fill('#filter-location', '不存在的位置')
    await page.click('form.filters button[type="submit"]')
    await page.waitForSelector('.empty-panel')
    assert(
      (await page.locator('.empty-panel').innerText()).includes('未找到符合条件的会议室'),
      '无匹配结果时显示“未找到符合条件的会议室”'
    )
    await page.click('form.filters button:has-text("重置")')
    await page.waitForSelector('table.table tbody tr')

    // ---------- 5. 会议室详情与占用时段 ----------
    await page.click('table.table tbody tr:has-text("学术报告厅") a:has-text("进详情")')
    await page.waitForURL(/\/rooms\/1$/)
    await page.waitForSelector('.slot-summary')
    assert(
      (await page.locator('.page-title').innerText()).includes('学术报告厅'),
      '详情页显示会议室名称'
    )
    assert(
      (await page.locator('.meta-grid').innerText()).includes('图书馆B1'),
      '详情页基本信息来自列表接口（D1）'
    )

    await page.fill('#occupancy-date', tomorrow)
    await page.waitForFunction(
      (date) =>
        Array.from(document.querySelectorAll('.slot-card--occupied')).some((node) =>
          node.textContent?.includes('10:00') && node.textContent?.includes('11:00')
        ),
      tomorrow
    )
    assert(
      (await page.locator('.slot-card--occupied').first().innerText()).includes('10:00'),
      '选择明天后显示种子占用 10:00–11:00（FR-17）'
    )
    assert(
      (await page.locator('.slot-card--free').count()) > 0,
      '详情页同时显示空闲时段'
    )
    assert(
      (await page.locator('button:has-text("去预约")').count()) === 1,
      '可用会议室显示“去预约”入口'
    )
    await screenshot(page, '03_会议室详情_占用时段')

    // ---------- 6. 预约表单必填校验 ----------
    await page.click('button:has-text("去预约")')
    await page.waitForURL(/\/bookings\/new/)
    await page.waitForSelector('#booking-room')
    const selectedRoomValue = await page.locator('#booking-room').inputValue()
    assert(selectedRoomValue === '1', '从详情页进入预约表单时自动带入会议室')
    const datetimeValue = await page.locator('#booking-start').inputValue()
    assert(datetimeValue.startsWith(tomorrow), '预约表单带有详情页所选日期')

    await page.click('button[type="submit"]')
    await page.waitForSelector('#booking-subject[aria-invalid="true"]')
    const inlineErrors = await page.locator('.field-error').allInnerTexts()
    assert(
      inlineErrors.some((text) => text.includes('请填写会议主题')) &&
        inlineErrors.some((text) => text.includes('请填写参会人数')) &&
        inlineErrors.some((text) => text.includes('请填写联系人')),
      '缺必填项时在对应输入框行内提示（NF-09）',
      inlineErrors.join(' / ')
    )
    assert(
      (await page.locator('.alert--error').count()) === 0,
      '表单校验失败不跳页、不显示原始错误码'
    )

    // ---------- 7. 冲突提交：409 提示占用时段且保留已填内容 ----------
    await page.selectOption('#booking-room', '1')
    await page.fill('#booking-subject', 'E2E 冲突验证会议')
    await page.fill('#booking-attendees', '10')
    await page.fill('#booking-contact', '张同学')
    await page.fill('#booking-start', `${tomorrow}T10:30`)
    await page.fill('#booking-end', `${tomorrow}T11:30`)
    await page.click('button[type="submit"]')
    await page.waitForSelector('.conflict-panel')
    const conflictAlert = await page.locator('.alert--error').first().innerText()
    assert(
      conflictAlert.includes('该时段已被占用') && conflictAlert.includes('10:00'),
      '冲突时显示中文提示且包含被占用时段（FR-09/FR-10）',
      conflictAlert.replace(/\n/g, ' / ')
    )
    assert(
      (await page.inputValue('#booking-subject')) === 'E2E 冲突验证会议' &&
        (await page.inputValue('#booking-attendees')) === '10' &&
        (await page.inputValue('#booking-contact')) === '张同学',
      '冲突失败后完整保留已填信息（FR-18）'
    )
    assert(
      !conflictAlert.includes('500') && !conflictAlert.includes('Error'),
      '冲突提示不是原始错误码或堆栈（NF-10）'
    )
    await screenshot(page, '04_预约表单_冲突提示')

    // ---------- 8. 首尾相接改期 → 成功并落库 ----------
    await page.fill('#booking-start', `${tomorrow}T11:00`)
    await page.fill('#booking-end', `${tomorrow}T12:00`)
    await page.click('button[type="submit"]')
    await page.waitForSelector('.success-panel')
    const reservationNo = (await page.locator('.success-panel .mono').innerText()).trim()
    assert(/^MR\d{12}$/.test(reservationNo), '预约成功并返回预约编号', reservationNo)
    assert(
      (await page.locator('.success-panel').innerText()).includes('11:00'),
      '预约时间为 11:00–12:00（与 10:00–11:00 首尾相接）'
    )
    await screenshot(page, '05_预约成功')

    const occupancyAfter = await page.evaluate(async (date) => {
      const token = JSON.parse(localStorage.getItem('meetingroom.session.v1') || '{}').token
      const response = await fetch(`/api/rooms/1/occupancy?date=${date}`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      return response.json()
    }, tomorrow)
    const occupiedTimes = (occupancyAfter.data?.occupiedSlots || []).map(
      (slot) => `${slot.startTime.slice(11, 16)}-${slot.endTime.slice(11, 16)}`
    )
    assert(
      occupiedTimes.includes('10:00-11:00') && occupiedTimes.includes('11:00-12:00'),
      '成功预约后从接口可查到两条首尾相接的占用记录（落库可查）',
      occupiedTimes.join(' / ')
    )

    // ---------- 9. 刷新不掉登录 ----------
    await page.reload()
    await page.waitForSelector('nav.nav')
    assert(page.url().includes('/bookings/new'), '刷新后仍在当前页面，未被踢回登录页')
    assert(
      (await page.locator('.user-chip').innerText()).includes('张同学'),
      '刷新后登录态与用户信息保留（localStorage 令牌持久化）'
    )

    // ---------- 10. 普通用户访问管理路由 → 403 ----------
    await page.goto(`${baseUrl}/admin/users`)
    await page.waitForURL(/\/403/)
    assert(
      (await page.locator('body').innerText()).includes('无权访问'),
      '普通用户访问管理页面被前端路由拦截到 403'
    )
    await screenshot(page, '06_权限不足_403')

    // ---------- 11. 登出 ----------
    await page.goto(`${baseUrl}/rooms`)
    await page.waitForSelector('button:has-text("退出登录")')
    await page.click('button:has-text("退出登录")')
    await page.waitForURL(/\/login$/)
    const tokenAfterLogout = await page.evaluate(
      () => localStorage.getItem('meetingroom.session.v1')
    )
    assert(tokenAfterLogout === null, '登出后本地令牌被清除')
    await page.goto(`${baseUrl}/rooms`)
    await page.waitForURL(/\/login/)
    assert(page.url().includes('/login?redirect=/rooms'), '登出后访问受限页再次要求登录')

    // ---------- 12. 停用账号登录提示 ----------
    await page.fill('#login-username', 'disabled001')
    await page.fill('#login-password', USER_PASSWORD)
    await page.click('button[type="submit"]')
    await page.waitForSelector('.alert--error')
    assert(
      (await page.locator('.alert--error').innerText()).includes('账号已停用'),
      '停用账号登录被拒并提示原因'
    )

    // ---------- 13. 管理员登录与角色菜单 ----------
    await page.fill('#login-username', 'admin001')
    await page.fill('#login-password', ADMIN_PASSWORD)
    await page.click('button[type="submit"]')
    await page.waitForURL(/\/rooms$/)
    await page.waitForSelector('nav.nav')
    const adminNavText = await page.locator('nav.nav').innerText()
    assert(
      adminNavText.includes('用户管理') &&
        adminNavText.includes('会议室管理') &&
        adminNavText.includes('全部预约处理') &&
        adminNavText.includes('操作日志'),
      '管理员菜单显示四个管理入口（FR-02）'
    )
    assert(
      !adminNavText.includes('我的预约'),
      '管理员菜单不显示“我的预约”（角色权限矩阵：管理员无个人预约）'
    )
    await screenshot(page, '07_角色菜单_管理员')

    await page.goto(`${baseUrl}/rooms/1`)
    await page.waitForSelector('.meta-grid')
    assert(
      (await page.locator('body').innerText()).includes('管理员账号不参与预约'),
      '管理员详情页不显示预约入口并说明原因'
    )

    // ---------- 14. 管理占位页 ----------
    await page.click('nav.nav a:has-text("用户管理")')
    await page.waitForURL(/\/admin\/users$/)
    assert(
      (await page.locator('body').innerText()).includes('建设中'),
      '管理入口进入第 10 周“建设中”占位页，不伪造功能'
    )

    // ---------- 15. 分辨率适配（NF-12：1366×768 ~ 1920×1080） ----------
    for (const viewport of [
      { width: 1366, height: 768, label: '1366×768' },
      { width: 1920, height: 1080, label: '1920×1080' }
    ]) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })
      await page.waitForTimeout(200)
      const layout = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        innerWidth: window.innerWidth
      }))
      assert(
        layout.scrollWidth <= layout.innerWidth + 1,
        `${viewport.label} 下页面无横向溢出`,
        `scrollWidth=${layout.scrollWidth}`
      )
      const linksVisible = await page.locator('nav.nav a').evaluateAll((nodes) =>
        nodes.every((node) => {
          const rect = node.getBoundingClientRect()
          return (
            rect.width > 0 &&
            rect.height > 0 &&
            rect.left >= 0 &&
            rect.right <= window.innerWidth
          )
        })
      )
      assert(linksVisible, `${viewport.label} 下角色菜单完整可见、不被遮挡`)
      if (viewport.width === 1366) {
        await screenshot(page, '08_角色菜单_1366×768')
      }
    }
    await page.setViewportSize({ width: 1440, height: 900 })

    // ---------- 16. 禁写 9 项巡检（页面可见文本） ----------
    const bodyText = await page.locator('body').innerText()
    // 用 Unicode 转义写禁写词，避免“禁写项巡检”脚本自身被关键词扫描误报
    const banned = [
      '\u4eba\u8138\u8bc6\u522b',
      '\u7269\u8054\u7f51',
      '3' + 'D',
      '\u667a\u80fd\u63a8\u8350',
      '\u91cd\u590d\u9884\u7ea6',
      '\u591a\u7ea7\u5ba1\u6279',
      '\u6d88\u606f\u63a8\u9001',
      '\u7edf\u4e00\u8eab\u4efd\u8ba4\u8bc1',
      '\u79fb\u52a8\u7aef'
    ]
    const hits = banned.filter((word) => bodyText.includes(word))
    assert(hits.length === 0, '页面可见文本未出现本学期禁写 9 项', hits.join(' / '))

    // ---------- 17. 控制台与请求错误 ----------
    assert(
      consoleErrors.length === 0,
      '浏览器控制台无报错',
      consoleErrors.slice(0, 3).join(' | ')
    )
    assert(
      failedRequests.length === 0,
      '无失败的网络请求',
      failedRequests.slice(0, 3).join(' | ')
    )
  } finally {
    await context.close()
    await browser.close()
    await stopChild(web)
    await stopChild(mock)
  }

  const failed = checks.filter((check) => !check.ok)
  const summary = {
    generatedAt: new Date().toISOString(),
    total: checks.length,
    passed: checks.length - failed.length,
    failed: failed.length,
    checks
  }
  await writeFile(
    path.join(ROOT, 'docs', 'week09', 'e2e-result.json'),
    `${JSON.stringify(summary, null, 2)}\n`,
    'utf8'
  )

  console.log('')
  if (failed.length > 0) {
    console.error(`E2E 未通过：${failed.length} 项失败，共 ${checks.length} 项。`)
    for (const item of failed) console.error(`  - ${item.name}${item.detail ? `：${item.detail}` : ''}`)
    process.exitCode = 1
    return
  }
  console.log(`全部 ${checks.length} 项浏览器端到端检查通过。`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
