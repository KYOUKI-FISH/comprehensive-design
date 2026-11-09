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
const NEW_USER_PASSWORD = crypto.randomBytes(12).toString('base64url')

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

function dateTimeString(offsetMinutes) {
  const date = new Date(Date.now() + offsetMinutes * 60_000)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(
    date.getHours()
  )}:${pad2(date.getMinutes())}:00`
}

const tomorrow = dateString(1)
const dayAfterTomorrow = dateString(2)
const yesterday = dateString(-1)

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
  check(userLogin.body.data.expiresIn === 1800, '登录响应带 30 分钟有效期（NF-04）')

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
      endTime: `${dayAfterTomorrow}T01:00:00`
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

  // ---------- 我的预约 / 修改 / 取消 ----------
  const mine = await api(BASE, 'GET', '/bookings/mine', { token: userToken })
  check(
    mine.status === 200 &&
      Array.isArray(mine.body.data) &&
      mine.body.data.length >= 5 &&
      mine.body.data.every((item) => typeof item.status === 'string'),
    '我的预约返回当前用户的预约与四态状态（FR-11）'
  )
  check(
    mine.body.data.some(
      (item) =>
        item.reservationNo === successBooking.body.data.reservationNo &&
        item.status === 'PENDING'
    ),
    '第 4 步提交的预约在“我的预约”中显示为待开始（FR-11/NF-08）'
  )
  check(
    new Set(mine.body.data.map((item) => item.reservationNo)).size === mine.body.data.length,
    '预约编号在当前用户列表中互不相同'
  )

  // 修改冲突：把 11:00-12:00 改成 10:30-11:30，撞上种子 10:00-11:00
  const updateConflict = await api(
    BASE,
    'PUT',
    `/bookings/${successBooking.body.data.id}`,
    {
      token: userToken,
      body: {
        subject: '正常预约（改期）',
        attendees: 8,
        contact: '张同学',
        startTime: `${tomorrow}T10:30:00`,
        endTime: `${tomorrow}T11:30:00`
      }
    }
  )
  check(
    updateConflict.status === 409 &&
      Array.isArray(updateConflict.body.data.occupiedSlots) &&
      updateConflict.body.data.occupiedSlots.some(
        (slot) => slot.startTime === `${tomorrow}T10:00:00`
      ),
    '修改预约时重新检测冲突并返回被占用时段（FR-12）'
  )

  const updateSuccess = await api(
    BASE,
    'PUT',
    `/bookings/${successBooking.body.data.id}`,
    {
      token: userToken,
      body: {
        subject: '正常预约（已改用途）',
        attendees: 9,
        contact: '张同学',
        startTime: `${tomorrow}T13:00:00`,
        endTime: `${tomorrow}T14:00:00`
      }
    }
  )
  check(
    updateSuccess.status === 200 &&
      updateSuccess.body.data.subject === '正常预约（已改用途）' &&
      updateSuccess.body.data.attendees === 9 &&
      updateSuccess.body.data.startTime === `${tomorrow}T13:00:00` &&
      updateSuccess.body.data.endTime === `${tomorrow}T14:00:00`,
    '修改未开始预约成功，库中字段随之变化（FR-12）'
  )

  // 新建一条待取消的预约（room 6，明天 10:00-11:00）
  const toCancel = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 6,
      subject: '待取消预约',
      attendees: 6,
      contact: '张同学',
      startTime: `${tomorrow}T10:00:00`,
      endTime: `${tomorrow}T11:00:00`
    }
  })
  check(toCancel.status === 200, '创建一条待取消预约')

  const cancelResult = await api(BASE, 'DELETE', `/bookings/${toCancel.body.data.id}`, {
    token: userToken
  })
  check(
    cancelResult.status === 200 &&
      cancelResult.body.data.status === 'CANCELLED' &&
      cancelResult.body.data.cancelReason === '用户主动取消',
    '取消未开始预约成功，状态变为已取消（FR-13）'
  )

  const occupancyAfterCancel = await api(
    BASE,
    'GET',
    `/rooms/6/occupancy?date=${tomorrow}`,
    { token: userToken }
  )
  check(
    !occupancyAfterCancel.body.data.occupiedSlots.some(
      (slot) => slot.startTime === `${tomorrow}T10:00:00`
    ),
    '取消后该时段立即从占用中释放（FR-13/NF-08）'
  )

  const updateCancelled = await api(
    BASE,
    'PUT',
    `/bookings/${toCancel.body.data.id}`,
    {
      token: userToken,
      body: {
        subject: '已取消后再修改',
        attendees: 6,
        contact: '张同学',
        startTime: `${tomorrow}T10:00:00`,
        endTime: `${tomorrow}T11:00:00`
      }
    }
  )
  check(
    updateCancelled.status === 400 && updateCancelled.body.message.includes('已取消'),
    '已取消的预约不能再修改（FR-12）'
  )

  const cancelAgain = await api(BASE, 'DELETE', `/bookings/${toCancel.body.data.id}`, {
    token: userToken
  })
  check(
    cancelAgain.status === 400 && cancelAgain.body.message.includes('已取消'),
    '重复取消被拒并给出中文提示'
  )

  const otherUsersBooking = await api(BASE, 'PUT', '/bookings/1', {
    token: userToken,
    body: {
      subject: '越权修改',
      attendees: 5,
      contact: '张同学',
      startTime: `${tomorrow}T10:00:00`,
      endTime: `${tomorrow}T11:00:00`
    }
  })
  check(
    otherUsersBooking.status === 403,
    '普通用户不能修改他人的预约（NF-03）'
  )

  // 30 分钟口径：距开始 20 分钟的预约不可修改/取消
  const soonStart = dateTimeString(20)
  const soonEnd = dateTimeString(50)
  const soonBooking = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 6,
      subject: '30分钟口径测试',
      attendees: 4,
      contact: '张同学',
      startTime: soonStart,
      endTime: soonEnd
    }
  })
  check(soonBooking.status === 200, '创建距开始 20 分钟的预约（用于 30 分钟口径）')

  const soonUpdate = await api(BASE, 'PUT', `/bookings/${soonBooking.body.data.id}`, {
    token: userToken,
    body: {
      subject: '30分钟口径测试（修改）',
      attendees: 4,
      contact: '张同学',
      startTime: soonStart,
      endTime: soonEnd
    }
  })
  check(
    soonUpdate.status === 400 && soonUpdate.body.message.includes('不足 30 分钟'),
    '距开始不足 30 分钟不能修改（FR-12）'
  )

  const soonCancel = await api(BASE, 'DELETE', `/bookings/${soonBooking.body.data.id}`, {
    token: userToken
  })
  check(
    soonCancel.status === 400 && soonCancel.body.message.includes('不足 30 分钟'),
    '距开始不足 30 分钟不能取消（FR-13）'
  )

  // 状态四态：进行中
  const ongoingStart = dateTimeString(-5)
  const ongoingEnd = dateTimeString(25)
  const ongoing = await api(BASE, 'POST', '/bookings', {
    token: userToken,
    body: {
      roomId: 5,
      subject: '进行中状态测试',
      attendees: 4,
      contact: '张同学',
      startTime: ongoingStart,
      endTime: ongoingEnd
    }
  })
  check(ongoing.status === 200, '创建一条已开始的预约（用于进行中状态）')

  const mineAfterOngoing = await api(BASE, 'GET', '/bookings/mine', {
    token: userToken
  })
  check(
    mineAfterOngoing.body.data.some(
      (item) => item.id === ongoing.body.data.id && item.status === 'ONGOING'
    ),
    '读取时推导出“进行中”状态（NF-08）'
  )

  // 管理员登录与越权
  const userAdmin = await api(BASE, 'GET', '/admin/users', { token: userToken })
  check(
    userAdmin.status === 403 && userAdmin.body.message.includes('无权访问'),
    '普通用户令牌直调管理接口被拒 403（NF-03）'
  )

  const userLogs = await api(BASE, 'GET', '/admin/logs', { token: userToken })
  check(userLogs.status === 403, '普通用户不能查看操作日志（NF-14）')

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

  const adminMineForbidden = await api(BASE, 'GET', '/bookings/mine', {
    token: adminToken
  })
  check(
    adminMineForbidden.status === 403,
    '管理员没有“我的预约”（角色权限矩阵）'
  )

  const adminBookings = await api(BASE, 'GET', '/admin/bookings', {
    token: adminToken
  })
  check(
    adminBookings.status === 200 &&
      Array.isArray(adminBookings.body.data) &&
      adminBookings.body.data.length >= 6 &&
      adminBookings.body.data.every(
        (item) => typeof item.username === 'string' && typeof item.userName === 'string'
      ),
    '全部预约返回所有用户的记录并带预约人信息（FR-15）'
  )

  const adminByRoom = await api(BASE, 'GET', '/admin/bookings?roomId=1', {
    token: adminToken
  })
  check(
    adminByRoom.status === 200 &&
      adminByRoom.body.data.length > 0 &&
      adminByRoom.body.data.every((item) => item.roomId === 1),
    '全部预约按会议室筛选（FR-19 简化版）'
  )

  const adminByDate = await api(BASE, 'GET', `/admin/bookings?date=${tomorrow}`, {
    token: adminToken
  })
  check(
    adminByDate.status === 200 &&
      adminByDate.body.data.length > 0 &&
      adminByDate.body.data.every((item) => item.startTime.startsWith(tomorrow)),
    '全部预约按日期筛选（FR-19 简化版）'
  )

  const adminBadDate = await api(BASE, 'GET', '/admin/bookings?date=2026-13-40', {
    token: adminToken
  })
  check(adminBadDate.status === 400, '非法日期筛选返回 400')

  const adminBadRoom = await api(BASE, 'GET', '/admin/bookings?roomId=abc', {
    token: adminToken
  })
  check(adminBadRoom.status === 400, '非法会议室筛选返回 400')

  const logsBefore = await api(BASE, 'GET', '/admin/logs', { token: adminToken })
  check(
    logsBefore.status === 200 &&
      Array.isArray(logsBefore.body.data) &&
      logsBefore.body.data.length === 0,
    '初始操作日志为空，不伪造记录'
  )

  const pastBooking = adminBookings.body.data.find(
    (item) => item.startTime.startsWith(yesterday) && item.status === 'FINISHED'
  )
  check(Boolean(pastBooking), '全部预约中存在已结束预约（用于管理员强制取消演示）')

  const noReason = await api(
    BASE,
    'PUT',
    `/admin/bookings/${pastBooking.id}/cancel`,
    { token: adminToken, body: { reason: '   ' } }
  )
  check(
    noReason.status === 400 && noReason.body.message.includes('请填写取消原因'),
    '强制取消未填原因被拒（FR-16）'
  )

  const forceCancel = await api(
    BASE,
    'PUT',
    `/admin/bookings/${pastBooking.id}/cancel`,
    { token: adminToken, body: { reason: '会议室临时维修，原预约无法使用' } }
  )
  check(
    forceCancel.status === 200 &&
      forceCancel.body.data.status === 'CANCELLED' &&
      forceCancel.body.data.cancelReason === '会议室临时维修，原预约无法使用' &&
      forceCancel.body.data.cancelledBy === 'admin001',
    '管理员填写原因后强制取消成功（FR-16）'
  )

  const forceCancelAgain = await api(
    BASE,
    'PUT',
    `/admin/bookings/${pastBooking.id}/cancel`,
    { token: adminToken, body: { reason: '重复处理' } }
  )
  check(forceCancelAgain.status === 400, '重复强制取消被拒')

  const logsAfterCancel = await api(BASE, 'GET', '/admin/logs', { token: adminToken })
  const cancelLog = logsAfterCancel.body.data.find(
    (item) => item.operationType === 'FORCE_CANCEL'
  )
  check(
    Boolean(cancelLog) &&
      cancelLog.operatorUsername === 'admin001' &&
      typeof cancelLog.createdAt === 'string' &&
      cancelLog.targetType === 'BOOKING' &&
      cancelLog.beforeData?.status === 'FINISHED' &&
      cancelLog.afterData?.status === 'CANCELLED',
    '强制取消写入操作日志，含操作人/时间/类型/前后数据（NF-14）'
  )

  // ---------- 会议室管理 ----------
  const adminRooms = await api(BASE, 'GET', '/admin/rooms', { token: adminToken })
  check(
    adminRooms.status === 200 && adminRooms.body.data.length === 6,
    '管理端会议室列表返回 6 间（含停用）'
  )

  const createdRoom = await api(BASE, 'POST', '/admin/rooms', {
    token: adminToken,
    body: {
      name: '冒烟测试会议室',
      location: '测试楼1层',
      capacity: 15,
      equipment: '投影仪，白板',
      status: 'AVAILABLE'
    }
  })
  check(
    createdRoom.status === 200 &&
      createdRoom.body.data.id > 0 &&
      Array.isArray(createdRoom.body.data.equipment) &&
      createdRoom.body.data.equipment.length === 2,
    '新增会议室成功并规范化设备列表'
  )

  const duplicateRoom = await api(BASE, 'POST', '/admin/rooms', {
    token: adminToken,
    body: {
      name: '冒烟测试会议室',
      location: '测试楼2层',
      capacity: 10
    }
  })
  check(duplicateRoom.status === 409, '同名会议室被拒 409')

  const invalidRoom = await api(BASE, 'POST', '/admin/rooms', {
    token: adminToken,
    body: { name: '', location: '', capacity: 0 }
  })
  check(invalidRoom.status === 400, '会议室必填项缺失返回 400')

  const updatedRoom = await api(BASE, 'PUT', `/admin/rooms/${createdRoom.body.data.id}`, {
    token: adminToken,
    body: {
      name: '冒烟测试会议室（已改）',
      location: '测试楼3层',
      capacity: 18,
      equipment: ['投影仪'],
      status: 'DISABLED'
    }
  })
  check(
    updatedRoom.status === 200 &&
      updatedRoom.body.data.name === '冒烟测试会议室（已改）' &&
      updatedRoom.body.data.capacity === 18 &&
      updatedRoom.body.data.status === 'DISABLED',
    '修改会议室成功（含启停）'
  )

  const deleteRoomWithBooking = await api(BASE, 'DELETE', '/admin/rooms/1', {
    token: adminToken
  })
  check(
    deleteRoomWithBooking.status === 409 &&
      deleteRoomWithBooking.body.message === '该会议室存在有效预约，请先处理预约',
    '有有效预约的会议室删除被拒 409（FR-03）'
  )

  const deletedRoom = await api(BASE, 'DELETE', `/admin/rooms/${createdRoom.body.data.id}`, {
    token: adminToken
  })
  check(deletedRoom.status === 200, '无有效预约的会议室删除成功')

  // ---------- 用户管理 ----------
  const adminUsers = await api(BASE, 'GET', '/admin/users', { token: adminToken })
  check(
    adminUsers.status === 200 &&
      adminUsers.body.data.length === 4 &&
      adminUsers.body.data.every((item) => item.passwordHash === undefined),
    '用户列表返回 4 个账号且不泄露口令摘要'
  )

  const createdUser = await api(BASE, 'POST', '/admin/users', {
    token: adminToken,
    body: {
      username: 'smokeuser',
      name: '冒烟用户',
      department: '测试部门',
      role: 'USER',
      password: NEW_USER_PASSWORD
    }
  })
  check(
    createdUser.status === 200 &&
      createdUser.body.data.username === 'smokeuser' &&
      createdUser.body.data.status === 'ACTIVE',
    '新增用户成功（FR-03）'
  )

  const duplicateUser = await api(BASE, 'POST', '/admin/users', {
    token: adminToken,
    body: {
      username: 'smokeuser',
      name: '重复账号',
      role: 'USER',
      password: NEW_USER_PASSWORD
    }
  })
  check(duplicateUser.status === 409, '重复账号被拒 409')

  const invalidUsername = await api(BASE, 'POST', '/admin/users', {
    token: adminToken,
    body: {
      username: 'a!',
      name: '非法账号',
      role: 'USER',
      password: NEW_USER_PASSWORD
    }
  })
  check(invalidUsername.status === 400, '非法账号格式返回 400')

  const shortPassword = await api(BASE, 'POST', '/admin/users', {
    token: adminToken,
    body: {
      username: 'shortpwd',
      name: '短口令',
      role: 'USER',
      password: '123'
    }
  })
  check(shortPassword.status === 400, '初始密码过短返回 400')

  const disabledUser = await api(BASE, 'PUT', `/admin/users/${createdUser.body.data.id}`, {
    token: adminToken,
    body: { status: 'DISABLED' }
  })
  check(
    disabledUser.status === 200 && disabledUser.body.data.status === 'DISABLED',
    '停用用户成功'
  )

  const loginDisabledNewUser = await api(BASE, 'POST', '/auth/login', {
    body: { username: 'smokeuser', password: NEW_USER_PASSWORD }
  })
  check(
    loginDisabledNewUser.status === 403 && loginDisabledNewUser.body.message.includes('停用'),
    '被停用账号不能登录，会话失效'
  )

  const promoteUser = await api(BASE, 'PUT', `/admin/users/${createdUser.body.data.id}`, {
    token: adminToken,
    body: { role: 'ADMIN', status: 'ACTIVE' }
  })
  check(
    promoteUser.status === 200 && promoteUser.body.data.role === 'ADMIN',
    '修改用户角色成功（角色变更留痕）'
  )

  const loginNewAdmin = await api(BASE, 'POST', '/auth/login', {
    body: { username: 'smokeuser', password: NEW_USER_PASSWORD }
  })
  check(
    loginNewAdmin.status === 200 && loginNewAdmin.body.data.user.role === 'ADMIN',
    '新建用户重置为启用后可登录，角色生效'
  )

  const selfDisable = await api(BASE, 'PUT', '/admin/users/3', {
    token: adminToken,
    body: { status: 'DISABLED' }
  })
  check(
    selfDisable.status === 400 && selfDisable.body.message.includes('不能停用'),
    '管理员不能停用当前登录账号'
  )

  const selfRoleChange = await api(BASE, 'PUT', '/admin/users/3', {
    token: adminToken,
    body: { role: 'USER' }
  })
  check(
    selfRoleChange.status === 400 && selfRoleChange.body.message.includes('角色'),
    '管理员不能修改当前登录账号的角色'
  )

  const logsAfterAll = await api(BASE, 'GET', '/admin/logs', { token: adminToken })
  const logTypes = new Set(logsAfterAll.body.data.map((item) => item.operationType))
  check(
    logsAfterAll.status === 200 &&
      logsAfterAll.body.data.length >= 7 &&
      ['CREATE_USER', 'UPDATE_USER', 'ROLE_CHANGE', 'CREATE_ROOM', 'UPDATE_ROOM', 'DELETE_ROOM', 'FORCE_CANCEL'].every(
        (type) => logTypes.has(type)
      ),
    '四类敏感操作全部留痕，日志类型齐全（NF-14）'
  )
  check(
    logsAfterAll.body.data.every(
      (item) =>
        typeof item.operatorUsername === 'string' &&
        typeof item.createdAt === 'string' &&
        'beforeData' in item &&
        'afterData' in item
    ),
    '每条日志字段完整：操作人/时间/类型/变更前后数据（NF-14）'
  )
  check(
    logsAfterAll.body.data.every(
      (item, index, array) =>
        index === 0 || array[index - 1].createdAt >= item.createdAt
    ),
    '操作日志按时间倒序返回'
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
