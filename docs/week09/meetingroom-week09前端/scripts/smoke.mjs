import { spawn } from 'node:child_process'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const PORT = 8097
const EXPIRY_PORT = 8098
const BASE = `http://127.0.0.1:${PORT}/api`
const EXPIRY_BASE = `http://127.0.0.1:${EXPIRY_PORT}/api`

// 冒烟脚本每次运行随机生成演示口令，仓库里不出现可用口令
const USER_PASSWORD = crypto.randomBytes(12).toString('base64url')
const ADMIN_PASSWORD = crypto.randomBytes(12).toString('base64url')

let passed = 0
let currentServerOutput = ''

function check(condition, label, detail = '') {
  if (!condition) {
    throw new Error(`[FAIL] ${label}${detail ? ` | ${detail}` : ''}`)
  }
  passed += 1
  console.log(`[PASS] ${String(passed).padStart(2, '0')} ${label}`)
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

function dateString(offsetDays) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

const tomorrow = dateString(1)

async function api(base, method, requestPath, { token, body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${base}${requestPath}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })

  const text = await response.text()
  let payload = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = { raw: text }
    }
  }

  return { status: response.status, body: payload }
}

function startServer({ port, ttlSeconds }) {
  const child = spawn(process.execPath, ['mock-server/server.mjs'], {
    cwd: ROOT,
    env: {
      ...process.env,
      MOCK_PORT: String(port),
      MOCK_TOKEN_TTL_SECONDS: String(ttlSeconds),
      MOCK_USER_PASSWORD: USER_PASSWORD,
      MOCK_ADMIN_PASSWORD: ADMIN_PASSWORD,
      MOCK_QUIET_CREDENTIALS: '1'
    },
    stdio: ['ignore', 'pipe', 'pipe']
  })

  child.stdout.on('data', (chunk) => {
    currentServerOutput += chunk.toString()
  })
  child.stderr.on('data', (chunk) => {
    currentServerOutput += chunk.toString()
  })

  return child
}

async function waitForHealth(base, child) {
  for (let attempt = 0; attempt < 80; attempt += 1) {
    if (child.exitCode !== null) {
      throw new Error(`mock server 提前退出，code=${child.exitCode}`)
    }
    try {
      const response = await fetch(`${base}/health`)
      if (response.ok) return
    } catch {
      // 继续等待
    }
    await new Promise((resolve) => setTimeout(resolve, 100))
  }
  throw new Error('mock server 启动超时')
}

function stopServer(child) {
  if (child && child.exitCode === null) child.kill()
}

function overlaps(startA, endA, startB, endB) {
  return startA < endB && endA > startB
}

async function runMainSuite() {
  const health = await api(BASE, 'GET', '/health')
  check(health.status === 200 && health.body.data.status === 'ok', '健康检查可访问')

  const badLogin = await api(BASE, 'POST', '/auth/login', {
    body: { username: 'user001', password: 'wrong-password' }
  })
  check(
    badLogin.status === 401 && badLogin.body.message === '账号或密码错误',
    '错误密码返回同一句中文提示（防账号枚举）',
    JSON.stringify(badLogin.body)
  )

  const disabledLogin = await api(BASE, 'POST', '/auth/login', {
    body: { username: 'disabled001', password: USER_PASSWORD }
  })
  check(
    disabledLogin.status === 403 && disabledLogin.body.message.includes('停用'),
    '停用账号正确密码登录被拒并提示原因'
  )

  const userLogin = await api(BASE, 'POST', '/auth/login', {
    body: { username: 'user001', password: USER_PASSWORD }
  })
  const userToken = userLogin.body?.data?.token
  check(
    userLogin.status === 200 &&
      typeof userToken === 'string' &&
      userToken.length >= 32 &&
      userLogin.body.data.user.role === 'USER',
    '普通用户登录成功并拿到令牌'
  )
  check(
    userLogin.body.data.expiresIn === 1800,
    '登录响应带 30 分钟有效期（NF-04）'
  )

  const anonRooms = await api(BASE, 'GET', '/rooms')
  check(anonRooms.status === 401, '未携带令牌访问会议室列表返回 401')

  const rooms = await api(BASE, 'GET', '/rooms', { token: userToken })
  check(
    rooms.status === 200 &&
      Array.isArray(rooms.body.data) &&
      rooms.body.data.length === 6 &&
      rooms.body.data.some((room) => room.status === 'DISABLED'),
    '会议室列表返回 6 间且包含停用状态（FR-06）'
  )

  const byName = await api(BASE, 'GET', '/rooms?name=301', { token: userToken })
  check(
    byName.status === 200 &&
      byName.body.data.length === 1 &&
      byName.body.data[0].name === '3号楼301',
    '按名称筛选会议室（FR-05）'
  )

  const byCapacity = await api(BASE, 'GET', '/rooms?minCapacity=100', {
    token: userToken
  })
  check(
    byCapacity.status === 200 &&
      byCapacity.body.data.length === 1 &&
      byCapacity.body.data[0].capacity === 120,
    '按最小容量筛选会议室（FR-05）'
  )

  const byLocation = await api(BASE, 'GET', '/rooms?location=行政楼', {
    token: userToken
  })
  check(
    byLocation.status === 200 &&
      byLocation.body.data.length === 1 &&
      byLocation.body.data[0].location === '行政楼2层',
    '按位置筛选会议室（FR-05）'
  )

  const byCombination = await api(
    BASE,
    'GET',
    '/rooms?location=%E5%8F%B7%E6%A5%BC&minCapacity=20',
    { token: userToken }
  )
  check(
    byCombination.status === 200 && byCombination.body.data.length === 2,
    '多条件组合筛选取交集（FR-05）'
  )

  const emptyRooms = await api(BASE, 'GET', '/rooms?name=不存在的会议室', {
    token: userToken
  })
  check(
    emptyRooms.status === 200 && emptyRooms.body.data.length === 0,
    '无匹配结果时返回空数组，页面显示“未找到”'
  )

  const badCapacity = await api(BASE, 'GET', '/rooms?minCapacity=abc', {
    token: userToken
  })
  check(badCapacity.status === 400, '非法容量筛选条件返回 400 中文提示')

  const occupancy = await api(
    BASE,
    'GET',
    `/rooms/1/occupancy?date=${tomorrow}`,
    { token: userToken }
  )
  check(
    occupancy.status === 200 &&
      occupancy.body.data.occupiedSlots.length === 1 &&
      occupancy.body.data.occupiedSlots[0].startTime === `${tomorrow}T10:00:00` &&
      occupancy.body.data.occupiedSlots[0].endTime === `${tomorrow}T11:00:00`,
    '占用时段接口返回种子预约 10:00-11:00（FR-17）'
  )
  const firstOccupied = occupancy.body.data.occupiedSlots[0]
  const allFree = occupancy.body.data.freeSlots.every(
    (slot) => !overlaps(slot.startTime, slot.endTime, firstOccupied.startTime, firstOccupied.endTime)
  )
  check(
    allFree &&
      occupancy.body.data.freeSlots.some((slot) => slot.startTime === `${tomorrow}T08:00:00`) &&
      occupancy.body.data.freeSlots.some((slot) => slot.startTime === `${tomorrow}T11:00:00`),
    '空闲时段与占用时段不重叠，且正确切分（FR-17）'
  )

  const badDate = await api(BASE, 'GET', '/rooms/1/occupancy?date=2026-13-40', {
    token: userToken
  })
  check(badDate.status === 400, '非法日期返回 400 中文提示')

  const missingFields = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '',
      attendees: 10,
      contact: '',
      startTime: `${tomorrow}T12:00:00`,
      endTime: `${tomorrow}T13:00:00`
    }
  })
  check(
    missingFields.status === 400 &&
      missingFields.body.message.includes('会议主题') &&
      missingFields.body.message.includes('联系人'),
    '缺必填项返回 400 并列出需要补充的字段（FR-07）'
  )

  const endBeforeStart = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '时间倒置测试',
      attendees: 5,
      contact: '张同学',
      startTime: `${tomorrow}T15:00:00`,
      endTime: `${tomorrow}T14:00:00`
    }
  })
  check(
    endBeforeStart.status === 400 &&
      endBeforeStart.body.message.includes('结束时间必须晚于开始时间'),
    '结束时间不晚于开始时间被拒（FR-08）'
  )

  const crossDay = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '跨天测试',
      attendees: 5,
      contact: '张同学',
      startTime: `${tomorrow}T23:00:00`,
      endTime: `${dateString(2)}T01:00:00`
    }
  })
  check(
    crossDay.status === 400 && crossDay.body.message.includes('不能跨天'),
    '跨天预约被拒（FR-08）'
  )

  const tooLong = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '超长测试',
      attendees: 5,
      contact: '张同学',
      startTime: `${tomorrow}T08:00:00`,
      endTime: `${tomorrow}T17:00:00`
    }
  })
  check(
    tooLong.status === 400 && tooLong.body.message.includes('8 小时'),
    '单次超过 8 小时被拒（FR-08）'
  )

  const disabledRoom = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 4,
      subject: '停用会议室测试',
      attendees: 5,
      contact: '张同学',
      startTime: `${tomorrow}T10:00:00`,
      endTime: `${tomorrow}T11:00:00`
    }
  })
  check(
    disabledRoom.status === 400 && disabledRoom.body.message.includes('已停用'),
    '停用会议室不能提交新预约（FR-06）'
  )

  const boundaryBefore = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '首尾相接（前）',
      attendees: 8,
      contact: '张同学',
      startTime: `${tomorrow}T09:00:00`,
      endTime: `${tomorrow}T10:00:00`
    }
  })
  check(
    boundaryBefore.status === 200 &&
      boundaryBefore.body.data.reservationNo.startsWith('MR') &&
      boundaryBefore.body.data.status === 'PENDING',
    '首尾相接（前）不冲突：09:00-10:00 可提交（FR-09）'
  )

  const conflict = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '冲突测试',
      attendees: 8,
      contact: '张同学',
      startTime: `${tomorrow}T10:30:00`,
      endTime: `${tomorrow}T11:30:00`
    }
  })
  const conflictSlots = conflict.body?.data?.occupiedSlots
  check(
    conflict.status === 409 &&
      Array.isArray(conflictSlots) &&
      conflictSlots.length === 1 &&
      conflictSlots[0].startTime === `${tomorrow}T10:00:00` &&
      conflictSlots[0].endTime === `${tomorrow}T11:00:00`,
    '冲突返回 409，且 data.occupiedSlots 带被占用时段（FR-10）'
  )

  const fullOverlap = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '完全重叠测试',
      attendees: 8,
      contact: '张同学',
      startTime: `${tomorrow}T10:00:00`,
      endTime: `${tomorrow}T11:00:00`
    }
  })
  check(fullOverlap.status === 409, '完全重叠被拒（FR-09）')

  const successBooking = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '正常预约',
      attendees: 8,
      contact: '张同学',
      startTime: `${tomorrow}T11:00:00`,
      endTime: `${tomorrow}T12:00:00`
    }
  })
  check(
    successBooking.status === 200 &&
      /^MR\d{12}$/.test(successBooking.body.data.reservationNo) &&
      successBooking.body.data.status === 'PENDING',
    '无冲突预约成功并返回预约编号（FR-07）'
  )

  const occupancyAfter = await api(
    BASE,
    'GET',
    `/rooms/1/occupancy?date=${tomorrow}`,
    { token: userToken }
  )
  check(
    occupancyAfter.body.data.occupiedSlots.some(
      (slot) =>
        slot.startTime === `${tomorrow}T11:00:00` &&
        slot.endTime === `${tomorrow}T12:00:00`
    ),
    '新预约立刻出现在占用时段中（落库可查）'
  )

  const boundaryAfter = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 1,
      subject: '首尾相接（后）',
      attendees: 8,
      contact: '张同学',
      startTime: `${tomorrow}T12:00:00`,
      endTime: `${tomorrow}T13:00:00`
    }
  })
  check(boundaryAfter.status === 200, '首尾相接（后）不冲突：12:00-13:00 可提交（FR-09）')

  const otherRoom = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 5,
      subject: '同日不同会议室',
      attendees: 10,
      contact: '张同学',
      startTime: `${tomorrow}T10:00:00`,
      endTime: `${tomorrow}T11:00:00`
    }
  })
  check(otherRoom.status === 200, '同日不同会议室不冲突（FR-09）')

  const mine = await api(BASE, 'GET', '/bookings/mine', { token: userToken })
  check(
    mine.status === 501 && mine.body.message.includes('第 10 周'),
    '“我的预约”如实返回未实现，不伪造数据'
  )

  const userAdmin = await api(BASE, 'GET', '/admin/users', { token: userToken })
  check(
    userAdmin.status === 403 && userAdmin.body.message.includes('无权访问'),
    '普通用户令牌直调管理接口被拒 403（NF-03）'
  )

  const adminLogin = await api(BASE, 'POST', '/auth/login', {
    body: { username: 'admin001', password: ADMIN_PASSWORD }
  })
  const adminToken = adminLogin.body?.data?.token
  check(
    adminLogin.status === 200 && adminLogin.body.data.user.role === 'ADMIN',
    '管理员登录成功，角色为 ADMIN'
  )

  const adminBooking = await api(BASE, 'POST', '/bookings', {
    token: adminToken,
    body: {
      roomId: 1,
      subject: '管理员预约测试',
      attendees: 5,
      contact: '王管理员',
      startTime: `${tomorrow}T16:00:00`,
      endTime: `${tomorrow}T17:00:00`
    }
  })
  check(
    adminBooking.status === 403 && adminBooking.body.message.includes('管理员'),
    '管理员账号不能提交个人预约（角色矩阵）'
  )

  const adminBookings = await api(BASE, 'GET', '/bookings', { token: adminToken })
  check(
    adminBookings.status === 501 && adminBookings.body.message.includes('第 10 周'),
    '管理员通过角色校验后，全部预约接口如实返回第 10 周实现'
  )

  const logout = await api(BASE, 'POST', '/auth/logout', { token: userToken })
  check(logout.status === 200, '登出成功')

  const afterLogout = await api(BASE, 'GET', '/rooms', { token: userToken })
  check(afterLogout.status === 401, '登出后旧令牌立即失效（NF-04）')
}

async function runExpirySuite() {
  const health = await fetch(`${EXPIRY_BASE}/health`)
  check(health.ok, '过期测试服务已启动')

  const login = await api(EXPIRY_BASE, 'POST', '/auth/login', {
    body: { username: 'user001', password: USER_PASSWORD }
  })
  const token = login.body?.data?.token
  check(login.status === 200 && Boolean(token), '过期测试登录成功')

  await new Promise((resolve) => setTimeout(resolve, 1300))

  const expired = await api(EXPIRY_BASE, 'GET', '/rooms', { token })
  check(
    expired.status === 401 && expired.body.message.includes('登录已过期'),
    '令牌到期后被拒，返回 401 中文提示（NF-04）'
  )
}

let mainServer
let expiryServer

try {
  mainServer = startServer({ port: PORT, ttlSeconds: 1800 })
  await waitForHealth(BASE, mainServer)
  await runMainSuite()

  expiryServer = startServer({ port: EXPIRY_PORT, ttlSeconds: 1 })
  await waitForHealth(EXPIRY_BASE, expiryServer)
  await runExpirySuite()

  console.log('')
  console.log(`全部 ${passed} 项冒烟检查通过。`)
} catch (error) {
  console.error('')
  console.error(error instanceof Error ? error.message : error)
  if (currentServerOutput) {
    console.error('')
    console.error('--- mock server 输出 ---')
    console.error(currentServerOutput.trim())
  }
  process.exitCode = 1
} finally {
  stopServer(mainServer)
  stopServer(expiryServer)
}
