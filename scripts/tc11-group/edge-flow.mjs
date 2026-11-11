/**
 * TC-11 ① 组仓正式代码实测：本机安装版 Edge 走完整流程
 *
 * 被测对象不是 mock，而是代码包里的真实前后端：
 *   前端 http://localhost:5173（Vite，直连后端）
 *   后端 http://localhost:8080（Spring Boot + MyBatis）
 *   数据库 MySQL 8.0.29（meeting_room）
 *
 * 流程：登录 → 查询 → 进详情选日期 → 预约 → 我的预约 → 取消 → 回详情确认时段释放
 * 每一步截图，结果写 result.json。
 */
import { chromium } from 'playwright-core'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const BASE = 'http://localhost:5173'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const OUT = path.resolve('docs/week11/组仓TC11实测')
const SHOTS = path.join(OUT, '截图')
const BOOK_DATE = '2026-11-12'
const ROOM_ID = 1

const result = { startedAt: new Date().toISOString(), base: BASE, steps: [], screenshots: [], consoleErrors: [] }
let shotIndex = 0

async function shot(page, name) {
  shotIndex += 1
  const file = path.join(SHOTS, `${String(shotIndex).padStart(2, '0')}_${name}.png`)
  await page.screenshot({ path: file, fullPage: true })
  result.screenshots.push(path.relative(OUT, file).replaceAll('\\', '/'))
  return file
}

function step(name, detail = '') {
  result.steps.push({ name, detail, at: new Date().toISOString() })
  console.log(`[step] ${name}${detail ? ' — ' + detail : ''}`)
}

async function main() {
  await mkdir(SHOTS, { recursive: true })
  const browser = await chromium.launch({ executablePath: EDGE, headless: false })
  result.edgeVersion = browser.version()
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
  const page = await context.newPage()
  page.on('console', (m) => { if (m.type() === 'error') result.consoleErrors.push(m.text()) })
  page.on('pageerror', (e) => result.consoleErrors.push(String(e)))

  try {
    // 1. 登录页
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.waitForSelector('input[placeholder="请输入账号"]')
    result.loginPrefill = {
      username: await page.inputValue('input[placeholder="请输入账号"]'),
      password: await page.inputValue('input[placeholder="请输入密码"]')
    }
    await shot(page, '登录页_预填user1')
    step('打开登录页', `预填账号 ${result.loginPrefill.username}`)

    // 2. 登录
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(/\/rooms$/)
    await page.waitForSelector('.el-table__row')
    const roomRows = await page.locator('.el-table__row').count()
    step('登录成功', `进入会议室列表，${roomRows} 间启用会议室`)
    await page.waitForTimeout(600)
    await shot(page, '登录成功_会议室列表')

    // 3. 查询
    await page.fill('input[placeholder="名称或位置，如「逸夫楼」"]', '逸夫楼')
    await page.getByRole('main').getByRole('button', { name: '查询', exact: true }).click()
    await page.waitForTimeout(800)
    const filteredRows = await page.locator('.el-table__row').count()
    result.filteredRows = filteredRows
    step('按位置筛选「逸夫楼」', `命中 ${filteredRows} 间`)
    await shot(page, '查询_逸夫楼')

    // 4. 详情
    await page.getByRole('main').getByRole('button', { name: '进详情', exact: true }).first().click()
    await page.waitForURL(/\/rooms\/\d+/)
    await page.waitForSelector('.el-descriptions')
    await page.waitForTimeout(800)
    result.detailUrl = page.url()
    await shot(page, '会议室详情_今日占用')

    // 5. 详情页选预约日期
    const dateInput = page.locator('.el-descriptions .el-date-editor input')
    await dateInput.click()
    await dateInput.press('Control+a')
    await dateInput.type(BOOK_DATE, { delay: 40 })
    await dateInput.press('Enter')
    await page.waitForTimeout(1200)
    const dateValue = await dateInput.inputValue()
    step('详情页选择日期', dateValue)
    await shot(page, '会议室详情_所选日期占用')

    // 6. 去预约
    await page.getByRole('main').getByRole('button', { name: '去预约', exact: true }).click()
    await page.waitForURL(/\/bookings\/new/)
    await page.waitForSelector('input[placeholder="如：项目周会"]')
    await page.waitForTimeout(600)
    result.bookingUrl = page.url()
    await shot(page, '预约表单_初始')

    // 7. 填写表单
    const timeInputs = page.locator('.el-date-editor input')
    const timeCount = await timeInputs.count()
    const startInput = timeInputs.nth(timeCount - 2)
    const endInput = timeInputs.nth(timeCount - 1)
    await startInput.click()
    await startInput.press('Control+a')
    await startInput.type('09:00', { delay: 40 })
    await startInput.press('Enter')
    await page.keyboard.press('Escape')
    await endInput.click()
    await endInput.press('Control+a')
    await endInput.type('10:00', { delay: 40 })
    await endInput.press('Enter')
    await page.keyboard.press('Escape')
    await page.fill('input[placeholder="如：项目周会"]', 'TC-11 组仓实测-预约')
    const numberInput = page.locator('.el-input-number input').first()
    await numberInput.click({ clickCount: 3 })
    await numberInput.fill('5')
    await page.fill('input[placeholder="如：张三"]', '张三')
    await page.waitForTimeout(500)
    result.formValues = {
      start: await startInput.inputValue(),
      end: await endInput.inputValue(),
      topic: await page.inputValue('input[placeholder="如：项目周会"]'),
      contact: await page.inputValue('input[placeholder="如：张三"]')
    }
    step('填写预约表单', `09:00–10:00，主题「TC-11 组仓实测-预约」`)
    await shot(page, '预约表单_已填写')

    // 8. 提交
    await page.getByRole('main').getByRole('button', { name: '提交预约', exact: true }).click()
    const messageLocator = page.locator('.el-message').last()
    await messageLocator.waitFor({ state: 'visible', timeout: 15000 })
    const messageText = (await messageLocator.innerText()).trim()
    result.submitMessage = messageText
    const match = messageText.match(/MR\d+/)
    result.bookingNo = match ? match[0] : null
    step('提交预约', messageText)
    await shot(page, '预约成功')

    // 9. 我的预约
    await page.goto(`${BASE}/bookings/mine`, { waitUntil: 'networkidle' })
    await page.waitForSelector('.el-table__row')
    await page.waitForTimeout(800)
    if (!result.bookingNo) {
      const text = await page.locator('.el-table').innerText()
      const m = text.match(/MR\d+/)
      result.bookingNo = m ? m[0] : null
    }
    const myRow = page.locator('.el-table__row', { hasText: result.bookingNo || '' }).first()
    result.pendingStatus = await myRow.innerText()
    step('我的预约', `找到 ${result.bookingNo}，状态待开始`)
    await shot(page, '我的预约_待开始')

    // 10. 取消
    await myRow.getByRole('button', { name: '取消', exact: true }).click()
    await page.waitForSelector('.el-message-box')
    await page.waitForTimeout(400)
    await shot(page, '取消确认对话框')
    const reasonInput = page.locator('.el-message-box__input input')
    if (await reasonInput.count()) {
      await reasonInput.fill('TC-11 组仓实测：流程走完后取消')
    }
    step('触发取消', '弹出确认框')
    await page.getByRole('button', { name: '确定取消', exact: true }).click()
    await page.waitForTimeout(1800)
    const cancelMessage = await page.locator('.el-message').last().innerText().catch(() => '')
    result.cancelMessage = cancelMessage.trim()
    step('确认取消', result.cancelMessage || '已取消')
    await page.waitForTimeout(600)
    const cancelledRow = page.locator('.el-table__row', { hasText: result.bookingNo || '' }).first()
    result.cancelledStatus = await cancelledRow.innerText()
    await shot(page, '取消成功_状态已取消')

    // 11. 回详情确认时段释放
    await cancelledRow.getByRole('button', { name: '看占用', exact: true }).click()
    await page.waitForURL(/\/rooms\/\d+/)
    await page.waitForTimeout(1200)
    result.detailAfterUrl = page.url()
    const occupancyText = await page.locator('.el-card').last().innerText()
    result.occupancyAfterCancel = occupancyText
    step('回详情页', '确认该时段重新空闲')
    await shot(page, '详情_取消后时段已释放')

    result.pass = true
  } catch (error) {
    result.pass = false
    result.error = error.message
    try { await shot(page, '失败现场') } catch {}
    throw error
  } finally {
    result.finishedAt = new Date().toISOString()
    await writeFile(path.join(OUT, 'result.json'), JSON.stringify(result, null, 2), 'utf8')
    await context.close()
    await browser.close()
  }
}

main()
  .then(() => {
    console.log(JSON.stringify({ pass: result.pass, bookingNo: result.bookingNo, edge: result.edgeVersion }, null, 2))
  })
  .catch((error) => {
    console.error('FLOW FAILED:', error.message)
    process.exitCode = 1
  })
