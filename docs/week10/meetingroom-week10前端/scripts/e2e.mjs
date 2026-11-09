import { spawn } from 'node:child_process'
import crypto from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

/**
 * 第 10 周前端端到端验证（开发自测工具，不属于第 11 周正式测试材料）。
 * - 自己拉起 mock 后端与 Vite，验证完自行关闭
 * - 使用本机 Edge（playwright-core，不下载浏览器）
 * - 走通普通用户八步与管理员四步
 * - 截图输出到 docs/week10/自测截图/
 */

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const EDGE_PATH = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const MOCK_PORT = 18080
const WEB_PORT = 5199
const SCREENSHOT_DIR = path.join(ROOT, 'docs', 'week10', '自测截图')
// 每次运行随机生成演示口令，仓库里不出现可用口令
const USER_PASSWORD = crypto.randomBytes(12).toString('base64url')
const ADMIN_PASSWORD = crypto.randomBytes(12).toString('base64url')
const NEW_USER_PASSWORD = crypto.randomBytes(10).toString('base64url')

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

async function screenshot(page, name) {
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, `${name}.png`),
    fullPage: true
  })
}

/** 等待某个 API 响应；action 触发请求前先挂监听，避免竞态。 */
async function actAndWait(page, action, matcher, timeout = 15_000) {
  const pending = page.waitForResponse(matcher, { timeout })
  await action()
  return pending
}

function apiMatcher(fragment, method, status = 200) {
  return (response) =>
    response.url().includes(fragment) &&
    response.request().method() === method &&
    response.status() === status
}

async function waitForLoadingDone(page) {
  await page.waitForFunction(() => !document.querySelector('.loading'), null, {
    timeout: 10_000
  })
}

async function rowTexts(page) {
  return page.locator('table.table tbody tr').allInnerTexts()
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
    // 业务失败（登录 401、冲突 409、删除 409 等）浏览器会记一条资源错误，属预期；
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
    // ================= 普通用户八步 =================

    // ---------- 1. 未登录访问受限页 → 登录页 ----------
    await page.goto(`${baseUrl}/rooms`)
    await page.waitForURL(/\/login/)
    assert(
      page.url().includes('/login?redirect=/rooms'),
      '第1步：未登录访问会议室列表被重定向登录页并带 redirect'
    )

    // ---------- 2. 错误口令 → 中文提示 ----------
    await page.fill('#login-username', 'user001')
    await page.fill('#login-password', 'wrong-password')
    const badLogin = await actAndWait(
      page,
      () => page.click('button[type="submit"]'),
      apiMatcher('/api/auth/login', 'POST', 401)
    )
    assert(badLogin.status() === 401, '第1步：错误口令返回 401')
    await page.waitForSelector('.alert--error')
    assert(
      (await page.locator('.alert--error').innerText()).includes('账号或密码错误'),
      '第1步：登录失败显示中文业务提示（NF-10）'
    )
    await screenshot(page, '01_登录页')

    // ---------- 3. 普通用户登录 → 角色菜单 ----------
    await page.fill('#login-password', USER_PASSWORD)
    await actAndWait(
      page,
      () => page.click('button[type="submit"]'),
      apiMatcher('/api/auth/login', 'POST')
    )
    await page.waitForURL(/\/rooms$/)
    await page.waitForSelector('table.table tbody tr')
    assert(page.url().endsWith('/rooms'), '第2步：普通用户登录后进入会议室列表')

    const userNavText = await page.locator('nav.nav').innerText()
    assert(
      userNavText.includes('会议室查询') && userNavText.includes('我的预约'),
      '第2步：普通用户菜单包含“会议室查询”“我的预约”'
    )
    assert(
      !userNavText.includes('用户管理') &&
        !userNavText.includes('会议室管理') &&
        !userNavText.includes('全部预约处理') &&
        !userNavText.includes('操作日志'),
      '第2步：普通用户菜单不显示管理入口（FR-02）',
      userNavText.replace(/\n/g, ' / ')
    )
    assert(
      (await page.locator('table.table tbody tr').count()) === 6,
      '第2步：会议室列表显示后端返回的 6 间会议室'
    )
    await screenshot(page, '02_会议室列表_普通用户')

    // ---------- 4. 多条件筛选 ----------
    await page.fill('#filter-name', '301')
    await actAndWait(
      page,
      () => page.click('form.filters button[type="submit"]'),
      (response) => response.url().includes('/api/rooms?') && response.url().includes('name=301')
    )
    await waitForLoadingDone(page)
    assert(
      (await page.locator('table.table tbody tr').count()) === 1 &&
        (await page.locator('table.table tbody tr').innerText()).includes('3号楼301'),
      '第3步：按名称筛选命中 3号楼301（FR-05）'
    )

    await actAndWait(
      page,
      () => page.click('form.filters button:has-text("重置")'),
      (response) => response.url().includes('/api/rooms') && !response.url().includes('?')
    )
    await waitForLoadingDone(page)
    await page.fill('#filter-capacity', '100')
    await actAndWait(
      page,
      () => page.click('form.filters button[type="submit"]'),
      (response) =>
        response.url().includes('/api/rooms?') && response.url().includes('minCapacity=100')
    )
    await waitForLoadingDone(page)
    assert(
      (await page.locator('table.table tbody tr').count()) === 1 &&
        (await page.locator('table.table tbody tr').innerText()).includes('学术报告厅'),
      '第3步：按最小容量 100 人筛选命中 120 人报告厅（FR-05）'
    )

    await actAndWait(
      page,
      () => page.click('form.filters button:has-text("重置")'),
      (response) => response.url().includes('/api/rooms') && !response.url().includes('?')
    )
    await waitForLoadingDone(page)

    // ---------- 5. 会议室详情与占用时段 ----------
    await page.click('table.table tbody tr:has-text("学术报告厅") a:has-text("进详情")')
    await page.waitForURL(/\/rooms\/1$/)
    await page.waitForSelector('.slot-summary')
    assert(
      (await page.locator('.page-title').innerText()).includes('学术报告厅') &&
        (await page.locator('.meta-grid').innerText()).includes('图书馆B1'),
      '第3步：详情页显示名称与基本信息（D1 组合供数）'
    )

    await page.fill('#occupancy-date', tomorrow)
    await page.waitForFunction(
      () =>
        Array.from(document.querySelectorAll('.slot-card--occupied')).some(
          (node) => node.textContent?.includes('10:00') && node.textContent?.includes('11:00')
        ),
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('.slot-card--occupied').first().innerText()).includes('10:00'),
      '第3步：选择明天后显示种子占用 10:00–11:00（FR-17）'
    )
    assert(
      (await page.locator('.slot-card--free').count()) > 0,
      '第3步：详情页同时显示空闲时段'
    )
    await screenshot(page, '03_会议室详情_占用时段')

    // ---------- 6. 预约表单校验 / 冲突 / 成功 ----------
    await page.click('button:has-text("去预约")')
    await page.waitForURL(/\/bookings\/new/)
    await page.waitForSelector('#booking-room')
    assert(
      (await page.locator('#booking-room').inputValue()) === '1' &&
        (await page.locator('#booking-start').inputValue()).startsWith(tomorrow),
      '第4步：从详情页进入预约表单自动带入会议室和日期'
    )

    await page.click('button[type="submit"]')
    await page.waitForSelector('#booking-subject[aria-invalid="true"]')
    const inlineErrors = await page.locator('.field-error').allInnerTexts()
    assert(
      inlineErrors.some((text) => text.includes('请填写会议主题')) &&
        inlineErrors.some((text) => text.includes('请填写参会人数')) &&
        inlineErrors.some((text) => text.includes('请填写联系人')),
      '第4步：缺必填项时行内中文提示（NF-09）',
      inlineErrors.join(' / ')
    )

    await page.fill('#booking-subject', 'E2E 周会')
    await page.fill('#booking-attendees', '10')
    await page.fill('#booking-contact', '张同学')
    await page.fill('#booking-start', `${tomorrow}T10:30`)
    await page.fill('#booking-end', `${tomorrow}T11:30`)
    await actAndWait(
      page,
      () => page.click('button[type="submit"]'),
      apiMatcher('/api/bookings', 'POST', 409)
    )
    await page.waitForSelector('.conflict-panel')
    const conflictAlert = await page.locator('.alert--error').first().innerText()
    assert(
      conflictAlert.includes('该时段已被占用') && conflictAlert.includes('10:00'),
      '第5步：冲突提示包含中文原因与被占用时段（FR-10）',
      conflictAlert.replace(/\n/g, ' / ')
    )
    assert(
      (await page.inputValue('#booking-subject')) === 'E2E 周会' &&
        (await page.inputValue('#booking-attendees')) === '10' &&
        (await page.inputValue('#booking-contact')) === '张同学',
      '第5步：冲突失败后完整保留已填信息（FR-18）'
    )
    await screenshot(page, '04_预约表单_冲突提示')

    await page.fill('#booking-start', `${tomorrow}T11:00`)
    await page.fill('#booking-end', `${tomorrow}T12:00`)
    await actAndWait(
      page,
      () => page.click('button[type="submit"]'),
      apiMatcher('/api/bookings', 'POST')
    )
    await page.waitForSelector('.success-panel')
    const reservationNo = (await page.locator('.success-panel .mono').innerText()).trim()
    assert(/^MR\d{12}$/.test(reservationNo), '第5步：预约成功并返回预约编号', reservationNo)
    assert(
      (await page.locator('.success-panel').innerText()).includes('11:00'),
      '第5步：预约时间为 11:00–12:00（与 10:00–11:00 首尾相接）'
    )
    await screenshot(page, '05_预约成功')

    // ---------- 7. 我的预约：四态、修改 ----------
    await actAndWait(
      page,
      () => page.click('nav.nav a:has-text("我的预约")'),
      apiMatcher('/api/bookings/mine', 'GET')
    )
    await page.waitForURL(/\/bookings\/mine$/)
    await page.waitForSelector('table.table tbody tr')
    assert(
      (await page.locator('table.table tbody tr').count()) === 3,
      '第6步：我的预约显示当前账号的 3 条记录（2 条种子 + 1 条新建）'
    )
    const mineText = await page.locator('table.table').innerText()
    assert(
      mineText.includes('待开始') && mineText.includes('已取消') === false,
      '第6步：预约状态以后端四态展示（当前均为待开始）'
    )
    const createdRow = page.locator(`tr:has-text("${reservationNo}")`)
    assert(
      (await createdRow.innerText()).includes('待开始') &&
        (await createdRow.locator('button:has-text("修改")').count()) === 1 &&
        (await createdRow.locator('button:has-text("取消")').count()) === 1,
      '第6步：待开始预约展示“修改”“取消”按钮（FR-12/FR-13）'
    )
    await screenshot(page, '06_我的预约_普通用户')

    await createdRow.locator('button:has-text("修改")').click()
    await page.waitForSelector('#edit-subject')
    await screenshot(page, '07_修改预约弹窗')
    await page.fill('#edit-subject', '')
    await page.click('.modal button[type="submit"]')
    await page.waitForSelector('#edit-subject[aria-invalid="true"]')
    assert(
      (await page.locator('.modal .field-error').first().innerText()).includes('请填写会议主题'),
      '第6步：修改弹窗缺必填项行内校验'
    )
    await page.fill('#edit-subject', 'E2E 周会（已改期）')
    await page.fill('#edit-start', `${tomorrow}T12:00`)
    await page.fill('#edit-end', `${tomorrow}T13:00`)
    await actAndWait(
      page,
      () => page.click('.modal button[type="submit"]'),
      apiMatcher('/api/bookings/', 'PUT')
    )
    await page.waitForSelector('.alert--success')
    assert(
      (await page.locator('.alert--success').innerText()).includes('已修改'),
      '第6步：修改预约成功并显示中文提示'
    )
    await page.waitForFunction(
      (no) => {
        const row = Array.from(document.querySelectorAll('table.table tbody tr')).find((node) =>
          node.textContent?.includes(no)
        )
        return Boolean(row && row.textContent?.includes('12:00'))
      },
      reservationNo,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator(`tr:has-text("${reservationNo}")`).innerText()).includes('12:00'),
      '第6步：修改后列表显示新时段 12:00–13:00'
    )
    await screenshot(page, '08_修改成功')

    // ---------- 8. 取消预约 → 时段释放 ----------
    await page.locator(`tr:has-text("${reservationNo}")`).locator('button:has-text("取消")').click()
    await page.waitForSelector('#cancel-booking-title')
    await screenshot(page, '09_取消预约确认')
    await actAndWait(
      page,
      () => page.click('.modal button:has-text("确认取消预约")'),
      apiMatcher('/api/bookings/', 'DELETE')
    )
    await page.waitForSelector('.alert--success')
    await page.waitForFunction(
      (no) => {
        const row = Array.from(document.querySelectorAll('table.table tbody tr')).find((node) =>
          node.textContent?.includes(no)
        )
        return Boolean(row && row.textContent?.includes('已取消'))
      },
      reservationNo,
      { timeout: 10_000 }
    )
    const cancelledRowText = await page.locator(`tr:has-text("${reservationNo}")`).innerText()
    assert(
      (await page.locator('.alert--success').innerText()).includes('该时段已重新释放'),
      '第7步：取消成功提示明确说明时段已释放'
    )
    assert(
      cancelledRowText.includes('已取消') && cancelledRowText.includes('用户主动取消'),
      '第7步：取消后状态变为已取消并展示取消原因',
      cancelledRowText.replace(/\n/g, ' / ')
    )
    assert(
      (await page.locator(`tr:has-text("${reservationNo}")`).locator('button').count()) === 0,
      '第7步：已取消预约不再展示修改/取消按钮'
    )
    await screenshot(page, '10_取消成功_我的预约')

    // ---------- 9. 回到详情页确认时段释放 ----------
    await page.goto(`${baseUrl}/rooms/1`)
    await page.waitForSelector('.slot-summary')
    await actAndWait(
      page,
      () => page.fill('#occupancy-date', tomorrow),
      (response) =>
        response.url().includes('/api/rooms/1/occupancy') &&
        response.url().includes(`date=${tomorrow}`)
    )
    await page.waitForFunction(
      () => !document.querySelector('.loading'),
      null,
      { timeout: 10_000 }
    )

    // 空闲时段可能被合并为跨多个小时的整段，按时间区间判断 12:00–13:00 是否被覆盖
    const occupiedIntervals = await page.locator('.slot-card--occupied .slot-time').allInnerTexts()
    const freeIntervals = await page.locator('.slot-card--free .slot-time').allInnerTexts()
    const toMinutes = (value) => {
      const [hour, minute] = value.split(':').map(Number)
      return hour * 60 + minute
    }
    const parseRange = (text) => {
      const matched = /(\d{2}:\d{2})\s*[–-]\s*(\d{2}:\d{2})/.exec(text)
      if (!matched) return null
      return { start: toMinutes(matched[1]), end: toMinutes(matched[2]) }
    }
    const overlapsTarget = (range) => range && range.start < 13 * 60 && range.end > 12 * 60
    assert(
      occupiedIntervals.every((text) => !overlapsTarget(parseRange(text))),
      '第8步：取消后 12:00–13:00 不再出现在已占用时段'
    )
    assert(
      freeIntervals.some((text) => {
        const range = parseRange(text)
        return range && range.start <= 12 * 60 && range.end >= 13 * 60
      }),
      '第8步：取消后 12:00–13:00 立即恢复为空闲时段（NF-08）',
      freeIntervals.join(' / ')
    )
    await screenshot(page, '11_详情页_取消后时段释放')

    // ---------- 10. 普通用户访问管理路由 → 403 ----------
    await page.goto(`${baseUrl}/admin/bookings`)
    await page.waitForURL(/\/403/)
    assert(
      (await page.locator('body').innerText()).includes('无权访问'),
      '普通用户访问管理页面被前端路由拦截到 403'
    )

    // ---------- 11. 登出与停用账号 ----------
    await page.goto(`${baseUrl}/rooms`)
    await page.waitForSelector('button:has-text("退出登录")')
    await page.click('button:has-text("退出登录")')
    await page.waitForURL(/\/login$/)
    assert(
      (await page.evaluate(() => localStorage.getItem('meetingroom.session.v1'))) === null,
      '登出后本地令牌被清除'
    )

    await page.fill('#login-username', 'disabled001')
    await page.fill('#login-password', USER_PASSWORD)
    await actAndWait(
      page,
      () => page.click('button[type="submit"]'),
      apiMatcher('/api/auth/login', 'POST', 403)
    )
    await page.waitForSelector('.alert--error')
    assert(
      (await page.locator('.alert--error').innerText()).includes('账号已停用'),
      '停用账号登录被拒并提示中文原因'
    )

    // ================= 管理员四步 =================

    // ---------- 12. 管理员登录与角色菜单 ----------
    await page.fill('#login-username', 'admin001')
    await page.fill('#login-password', ADMIN_PASSWORD)
    await actAndWait(
      page,
      () => page.click('button[type="submit"]'),
      apiMatcher('/api/auth/login', 'POST')
    )
    await page.waitForURL(/\/rooms$/)
    await page.waitForSelector('nav.nav')
    const adminNavText = await page.locator('nav.nav').innerText()
    assert(
      adminNavText.includes('全部预约处理') &&
        adminNavText.includes('会议室管理') &&
        adminNavText.includes('用户管理') &&
        adminNavText.includes('操作日志'),
      '管理员第1步：菜单显示四个管理入口（FR-02）'
    )
    assert(
      !adminNavText.includes('我的预约'),
      '管理员第1步：菜单不显示“我的预约”（角色权限矩阵）'
    )
    await screenshot(page, '12_管理员菜单')

    await page.goto(`${baseUrl}/bookings/mine`)
    await page.waitForURL(/\/403/)
    assert(
      (await page.locator('body').innerText()).includes('无权访问'),
      '管理员访问“我的预约”被路由拦截到 403'
    )

    // ---------- 13. 全部预约：筛选 / 强制取消 ----------
    await actAndWait(
      page,
      () => page.click('nav.nav a:has-text("全部预约处理")'),
      apiMatcher('/api/admin/bookings', 'GET')
    )
    await page.waitForURL(/\/admin\/bookings$/)
    await page.waitForSelector('table.table tbody tr')
    const allBookingCount = await page.locator('table.table tbody tr').count()
    assert(allBookingCount === 5, '管理员第2步：全部预约列表返回 5 条记录', String(allBookingCount))

    await page.selectOption('#admin-room-filter', '2')
    await page.fill('#admin-date-filter', tomorrow)
    await actAndWait(
      page,
      () => page.click('form.filters button[type="submit"]'),
      (response) => {
        if (!response.url().includes('/api/admin/bookings?')) return false
        const url = new URL(response.url())
        return url.searchParams.get('roomId') === '2' && url.searchParams.get('date') === tomorrow
      }
    )
    await waitForLoadingDone(page)
    assert(
      (await page.locator('table.table tbody tr').count()) === 1 &&
        (await page.locator('table.table tbody tr').innerText()).includes('课题组周会'),
      '管理员第2步：按会议室 + 日期筛选命中课题组周会（FR-19）'
    )
    await screenshot(page, '13_全部预约_筛选')

    await actAndWait(
      page,
      () => page.click('form.filters button:has-text("重置")'),
      apiMatcher('/api/admin/bookings', 'GET')
    )
    await waitForLoadingDone(page)
    assert(
      (await page.locator('table.table tbody tr').count()) === 5,
      '管理员第2步：重置筛选恢复全部 5 条记录'
    )

    const finishedRow = page.locator('tr:has-text("已结束的学术研讨会")')
    await finishedRow.locator('button:has-text("强制取消")').click()
    await page.waitForSelector('#force-cancel-reason')
    await page.click('.modal button[type="submit"]')
    await page.waitForSelector('#force-cancel-reason[aria-invalid="true"]')
    assert(
      (await page.locator('.modal .field-error').innerText()).includes('请填写取消原因'),
      '管理员第3步：未填原因时前端拒绝提交并提示（FR-16）'
    )
    await screenshot(page, '14_强制取消_原因必填')

    await page.fill('#force-cancel-reason', 'E2E：场地临时维修')
    await actAndWait(
      page,
      () => page.click('.modal button[type="submit"]'),
      apiMatcher('/api/admin/bookings/', 'PUT')
    )
    await page.waitForSelector('.alert--success')
    assert(
      (await page.locator('.alert--success').innerText()).includes('操作日志已记录'),
      '管理员第3步：填写原因后强制取消成功并提示日志已记录'
    )
    await page.waitForFunction(
      () =>
        Array.from(document.querySelectorAll('table.table tbody tr')).some(
          (node) =>
            node.textContent?.includes('已结束的学术研讨会') &&
            node.textContent?.includes('E2E：场地临时维修')
        ),
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('tr:has-text("已结束的学术研讨会")').innerText()).includes('已取消'),
      '管理员第3步：已结束异常预约被强制取消并展示原因'
    )
    await screenshot(page, '15_强制取消成功')

    // ---------- 14. 会议室管理：新增 / 编辑 / 删除 / 409 ----------
    await actAndWait(
      page,
      () => page.click('nav.nav a:has-text("会议室管理")'),
      apiMatcher('/api/admin/rooms', 'GET')
    )
    await page.waitForURL(/\/admin\/rooms$/)
    await page.waitForSelector('table.table tbody tr')
    assert(
      (await page.locator('table.table tbody tr').count()) === 6,
      '会议室管理：列表显示 6 间会议室'
    )
    await screenshot(page, '16_会议室管理')

    await page.click('button:has-text("新增会议室")')
    await page.waitForSelector('#room-name')
    await page.fill('#room-name', 'E2E临时会议室')
    await page.fill('#room-location', 'E2E楼1层')
    await page.fill('#room-capacity', '10')
    await page.fill('#room-equipment', '投影仪，白板')
    await actAndWait(
      page,
      () => page.click('.modal button[type="submit"]'),
      apiMatcher('/api/admin/rooms', 'POST')
    )
    await page.waitForSelector('.alert--success')
    await page.waitForFunction(
      () => document.querySelectorAll('table.table tbody tr').length === 7,
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('.alert--success').innerText()).includes('已新增') &&
        (await page.locator('tr:has-text("E2E临时会议室")').innerText()).includes('E2E楼1层'),
      '会议室管理：新增会议室成功并出现在列表'
    )

    await page.locator('tr:has-text("E2E临时会议室")').locator('button:has-text("编辑")').click()
    await page.waitForSelector('#room-name')
    await page.fill('#room-name', 'E2E临时会议室（改）')
    await page.selectOption('#room-status', 'DISABLED')
    await actAndWait(
      page,
      () => page.click('.modal button[type="submit"]'),
      apiMatcher('/api/admin/rooms/', 'PUT')
    )
    await page.waitForSelector('.alert--success')
    await page.waitForFunction(
      () => {
        const row = Array.from(document.querySelectorAll('table.table tbody tr')).find((node) =>
          node.textContent?.includes('E2E临时会议室（改）')
        )
        return Boolean(row && row.textContent?.includes('已停用'))
      },
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('.alert--success').innerText()).includes('已更新'),
      '会议室管理：编辑名称并将状态改为已停用成功'
    )

    await page
      .locator('tr:has-text("E2E临时会议室（改）")')
      .locator('button:has-text("删除")')
      .click()
    await page.waitForSelector('#room-delete-title')
    await actAndWait(
      page,
      () => page.click('.modal button:has-text("确认删除")'),
      apiMatcher('/api/admin/rooms/', 'DELETE')
    )
    await page.waitForSelector('.alert--success')
    await page.waitForFunction(
      () => document.querySelectorAll('table.table tbody tr').length === 6,
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('.alert--success').innerText()).includes('已删除'),
      '会议室管理：删除无预约的会议室成功'
    )

    await page
      .locator('tr:has-text("学术报告厅")')
      .locator('button:has-text("删除")')
      .click()
    await page.waitForSelector('#room-delete-title')
    await actAndWait(
      page,
      () => page.click('.modal button:has-text("确认删除")'),
      apiMatcher('/api/admin/rooms/', 'DELETE', 409)
    )
    await page.waitForSelector('#room-delete-title')
    const deleteAlert = await page.locator('.modal .alert--error').innerText()
    assert(
      deleteAlert.includes('该会议室存在有效预约，请先处理预约'),
      '会议室管理：存在有效预约时删除被拒并显示 409 中文原因（FR-03）',
      deleteAlert
    )
    assert(
      (await page.locator('#room-delete-title').count()) === 1,
      '会议室管理：删除失败时弹窗保持打开，不误关闭'
    )
    await screenshot(page, '17_会议室删除被拒_409')
    await page.click('.modal button:has-text("暂不删除")')

    // ---------- 15. 用户管理：新增 / 停用 / 角色变更 / 自我保护 ----------
    await actAndWait(
      page,
      () => page.click('nav.nav a:has-text("用户管理")'),
      apiMatcher('/api/admin/users', 'GET')
    )
    await page.waitForURL(/\/admin\/users$/)
    await page.waitForSelector('table.table tbody tr')
    assert(
      (await page.locator('table.table tbody tr').count()) === 4,
      '用户管理：列表显示 4 个种子账号'
    )
    await screenshot(page, '18_用户管理')

    await page.click('button:has-text("新增用户")')
    await page.waitForSelector('#user-username')
    await page.fill('#user-username', 'e2e_user_01')
    await page.fill('#user-name', 'E2E测试用户')
    await page.fill('#user-department', '测试部')
    await page.fill('#user-password', NEW_USER_PASSWORD)
    await actAndWait(
      page,
      () => page.click('.modal button[type="submit"]'),
      apiMatcher('/api/admin/users', 'POST')
    )
    await page.waitForSelector('.alert--success')
    await page.waitForFunction(
      () => document.querySelectorAll('table.table tbody tr').length === 5,
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('.alert--success').innerText()).includes('已创建'),
      '用户管理：新增用户成功并提示可使用初始密码登录'
    )

    await page
      .locator('tr:has-text("e2e_user_01")')
      .locator('button:has-text("编辑")')
      .click()
    await page.waitForSelector('#user-status')
    await page.selectOption('#user-status', 'DISABLED')
    await actAndWait(
      page,
      () => page.click('.modal button[type="submit"]'),
      apiMatcher('/api/admin/users/', 'PUT')
    )
    await page.waitForSelector('.alert--success')
    await page.waitForFunction(
      () => {
        const row = Array.from(document.querySelectorAll('table.table tbody tr')).find((node) =>
          node.textContent?.includes('e2e_user_01')
        )
        return Boolean(row && row.textContent?.includes('已停用'))
      },
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('.alert--success').innerText()).includes('已更新'),
      '用户管理：停用账号成功（NF-14 会记日志）'
    )

    await page
      .locator('tr:has-text("e2e_user_01")')
      .locator('button:has-text("编辑")')
      .click()
    await page.waitForSelector('#user-role')
    await page.selectOption('#user-role', 'ADMIN')
    await actAndWait(
      page,
      () => page.click('.modal button[type="submit"]'),
      apiMatcher('/api/admin/users/', 'PUT')
    )
    await page.waitForSelector('.alert--success')
    await page.waitForFunction(
      () => {
        const row = Array.from(document.querySelectorAll('table.table tbody tr')).find((node) =>
          node.textContent?.includes('e2e_user_01')
        )
        return Boolean(row && row.textContent?.includes('管理员'))
      },
      null,
      { timeout: 10_000 }
    )
    assert(
      (await page.locator('.alert--success').innerText()).includes('已更新'),
      '用户管理：变更角色成功（NF-14 会记角色变更日志）'
    )

    await page.locator('tr:has-text("admin001")').locator('button:has-text("编辑")').click()
    await page.waitForSelector('#user-role')
    assert(
      (await page.locator('#user-role').isDisabled()) &&
        (await page.locator('#user-status').isDisabled()),
      '用户管理：当前登录账号的角色与状态不可修改（自我保护）'
    )
    await screenshot(page, '19_用户管理_自我保护')
    await page.click('.modal button:has-text("返回列表")')

    // ---------- 16. 操作日志 ----------
    await actAndWait(
      page,
      () => page.click('nav.nav a:has-text("操作日志")'),
      apiMatcher('/api/admin/logs', 'GET')
    )
    await page.waitForURL(/\/admin\/logs$/)
    await page.waitForSelector('table.table tbody tr')
    assert(
      (await page.locator('table.table tbody tr').count()) === 7,
      '管理员第4步：操作日志记录 7 条敏感操作',
      String(await page.locator('table.table tbody tr').count())
    )
    const logHeaders = (await page.locator('table.table thead').innerText()).replace(/\s+/g, ' ')
    assert(
      logHeaders.includes('操作时间') &&
        logHeaders.includes('操作人') &&
        logHeaders.includes('类型') &&
        logHeaders.includes('对象') &&
        logHeaders.includes('摘要') &&
        logHeaders.includes('变更前后'),
      '管理员第4步：日志字段完整（操作人/时间/类型/对象/摘要/变更前后）',
      logHeaders
    )
    const logText = await page.locator('table.table').innerText()
    const requiredTypes = [
      '强制取消预约',
      '新增会议室',
      '修改会议室',
      '删除会议室',
      '新增用户',
      '修改用户',
      '角色变更'
    ]
    const missingTypes = requiredTypes.filter((type) => !logText.includes(type))
    assert(
      missingTypes.length === 0,
      '管理员第4步：四类敏感操作日志均可在页面查到',
      missingTypes.join(' / ')
    )
    await screenshot(page, '20_操作日志')

    const firstDetails = page.locator('table.table tbody tr').first().locator('details')
    await firstDetails.locator('summary').click()
    await page.waitForSelector('table.table tbody tr:first-child details[open]')
    const beforeJson = await page
      .locator('table.table tbody tr')
      .first()
      .locator('.log-json')
      .first()
      .innerText()
    assert(
      beforeJson.includes('{') && beforeJson.includes('}'),
      '管理员第4步：变更前后数据可展开查看 JSON',
      beforeJson.replace(/\n/g, ' ').slice(0, 80)
    )
    await screenshot(page, '21_操作日志_展开变更')

    // ---------- 17. 分辨率适配（NF-12：1366×768 ~ 1920×1080） ----------
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
        `${viewport.label} 下操作日志页无横向溢出`,
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
      assert(linksVisible, `${viewport.label} 下管理员菜单完整可见`)
      if (viewport.width === 1366) {
        await screenshot(page, '22_操作日志_1366×768')
      }
    }
    await page.setViewportSize({ width: 1440, height: 900 })

    // ---------- 18. 禁写 9 项巡检（页面可见文本） ----------
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

    // ---------- 19. 控制台与请求错误 ----------
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
    week: 10,
    total: checks.length,
    passed: checks.length - failed.length,
    failed: failed.length,
    checks
  }
  await writeFile(
    path.join(ROOT, 'docs', 'week10', 'e2e-result.json'),
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
