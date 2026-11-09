import http from 'node:http'
import crypto from 'node:crypto'

/**
 * 第 11 周本地联调 mock 后端（不替代组内正式后端）。
 * - 数据仅存内存，重启即重置
 * - 令牌 30 分钟（可用 MOCK_TOKEN_TTL_SECONDS 覆盖）
 * - 冲突判定只实现一次：hasOverlap()，半开区间 [start, end)
 * - 状态四态在读取时按当前时间推导，取消状态落库
 * - 我的预约 / 修改 / 取消 / 管理端 4 类接口 / 操作日志均可用
 */

const PORT = Number(process.env.MOCK_PORT || 8080)
const TOKEN_TTL_SECONDS = Number(process.env.MOCK_TOKEN_TTL_SECONDS || 30 * 60)
const USER_PASSWORD = process.env.MOCK_USER_PASSWORD || crypto.randomBytes(12).toString('base64url')
const ADMIN_PASSWORD = process.env.MOCK_ADMIN_PASSWORD || crypto.randomBytes(12).toString('base64url')
const QUIET_CREDENTIALS = process.env.MOCK_QUIET_CREDENTIALS === '1'

const BUSINESS_START_MINUTES = 8 * 60
const BUSINESS_END_MINUTES = 22 * 60
const MODIFY_CUTOFF_MINUTES = 30
const MAX_BOOKING_HOURS = 8

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

function nowLocalString() {
  const date = new Date()
  return `${toDateString(date)}T${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex')
  const digest = crypto.scryptSync(password, salt, 32).toString('hex')
  return `${salt}:${digest}`
}

function verifyPassword(password, stored) {
  const [salt, digest] = stored.split(':')
  if (!salt || !digest) return false
  const actual = crypto.scryptSync(password, salt, 32)
  const expected = Buffer.from(digest, 'hex')
  return actual.length === expected.length && crypto.timingSafeEqual(actual, expected)
}

const users = [
  {
    id: 1,
    username: 'user001',
    name: '张同学',
    department: '计算机学院',
    role: 'USER',
    status: 'ACTIVE',
    passwordHash: hashPassword(USER_PASSWORD),
    createdAt: '2026-09-01T09:00:00'
  },
  {
    id: 2,
    username: 'user002',
    name: '李同学',
    department: '外国语学院',
    role: 'USER',
    status: 'ACTIVE',
    passwordHash: hashPassword(USER_PASSWORD),
    createdAt: '2026-09-01T09:05:00'
  },
  {
    id: 3,
    username: 'admin001',
    name: '王管理员',
    department: '信息中心',
    role: 'ADMIN',
    status: 'ACTIVE',
    passwordHash: hashPassword(ADMIN_PASSWORD),
    createdAt: '2026-09-01T09:10:00'
  },
  {
    id: 4,
    username: 'disabled001',
    name: '停用账号样例',
    department: '计算机学院',
    role: 'USER',
    status: 'DISABLED',
    passwordHash: hashPassword(USER_PASSWORD),
    createdAt: '2026-09-01T09:15:00'
  }
]

const rooms = [
  {
    id: 1,
    name: '学术报告厅',
    location: '图书馆B1',
    capacity: 120,
    equipment: ['投影仪', '音响', '麦克风'],
    status: 'AVAILABLE'
  },
  {
    id: 2,
    name: '3号楼301',
    location: '3号楼3层',
    capacity: 20,
    equipment: ['投影仪', '白板', '视频会议'],
    status: 'AVAILABLE'
  },
  {
    id: 3,
    name: '行政楼小会议室',
    location: '行政楼2层',
    capacity: 8,
    equipment: ['白板'],
    status: 'AVAILABLE'
  },
  {
    id: 4,
    name: '旧会议室（停用样例）',
    location: '1号楼1层',
    capacity: 12,
    equipment: ['投影仪'],
    status: 'DISABLED'
  },
  {
    id: 5,
    name: '6号楼研讨室',
    location: '6号楼5层',
    capacity: 30,
    equipment: ['投影仪', '白板'],
    status: 'AVAILABLE'
  },
  {
    id: 6,
    name: '工程训练中心报告厅',
    location: '工程训练中心',
    capacity: 80,
    equipment: ['投影仪', '音响'],
    status: 'AVAILABLE'
  }
]

const reservations = []
const operationLogs = []
let reservationSequence = 0
let roomSequence = rooms.length
let userSequence = users.length
let logSequence = 0
const tokens = new Map()

function toUserVO(user) {
  return {
    id: user.id,
    username: user.username,
    name: user.name,
    department: user.department,
    role: user.role,
    status: user.status
  }
}

function toAdminUserVO(user) {
  return { ...toUserVO(user), createdAt: user.createdAt || '' }
}

function toAdminUserSnapshot(user) {
  return {
    id: user.id,
    username: user.username,
    name: user.name,
    department: user.department,
    role: user.role,
    status: user.status
  }
}

function deriveStatus(record) {
  if (record.cancelled) return 'CANCELLED'
  const now = Date.now()
  const start = toMilliseconds(record.startTime)
  const end = toMilliseconds(record.endTime)
  if (now < start) return 'PENDING'
  if (now < end) return 'ONGOING'
  return 'FINISHED'
}

function createReservationRecord({
  roomId,
  userId,
  subject,
  attendees,
  contact,
  startTime,
  endTime,
  createdAt
}) {
  reservationSequence += 1
  const id = reservationSequence
  const datePart = startTime.slice(0, 10).replaceAll('-', '')
  const record = {
    id,
    // 编号口径：由自增主键派生，避免并发下“当日流水号”撞唯一键
    reservationNo: `MR${datePart}${String(id).padStart(4, '0')}`,
    roomId,
    userId,
    subject,
    attendees,
    contact,
    startTime,
    endTime,
    cancelled: false,
    cancelReason: '',
    cancelledBy: '',
    cancelledAt: '',
    createdAt: createdAt || nowLocalString()
  }
  reservations.push(record)
  return record
}

function toBookingVO(record) {
  const room = rooms.find((item) => item.id === record.roomId)
  return {
    id: record.id,
    reservationNo: record.reservationNo,
    roomId: record.roomId,
    roomName: room ? room.name : '',
    subject: record.subject,
    attendees: record.attendees,
    contact: record.contact,
    startTime: record.startTime,
    endTime: record.endTime,
    status: deriveStatus(record),
    createdAt: record.createdAt,
    ...(record.cancelled
      ? {
          cancelReason: record.cancelReason,
          cancelledBy: record.cancelledBy,
          cancelledAt: record.cancelledAt
        }
      : {})
  }
}

function toAdminBookingVO(record) {
  const user = users.find((item) => item.id === record.userId)
  return {
    ...toBookingVO(record),
    userId: record.userId,
    username: user ? user.username : '',
    userName: user ? user.name : '',
    department: user ? user.department : ''
  }
}

const tomorrow = toDateString(addDays(new Date(), 1))
const dayAfterTomorrow = toDateString(addDays(new Date(), 2))
const yesterday = toDateString(addDays(new Date(), -1))

createReservationRecord({
  roomId: 1,
  userId: 2,
  subject: '学院工作例会',
  attendees: 18,
  contact: '李同学',
  startTime: `${tomorrow}T10:00:00`,
  endTime: `${tomorrow}T11:00:00`
})

createReservationRecord({
  roomId: 2,
  userId: 1,
  subject: '课题组周会',
  attendees: 12,
  contact: '张同学',
  startTime: `${tomorrow}T14:00:00`,
  endTime: `${tomorrow}T16:00:00`
})

createReservationRecord({
  roomId: 3,
  userId: 1,
  subject: '师生座谈',
  attendees: 6,
  contact: '张同学',
  startTime: `${dayAfterTomorrow}T09:00:00`,
  endTime: `${dayAfterTomorrow}T10:00:00`
})

// 已结束预约：管理员四步里“取消一条已过期异常预约”的演示数据
createReservationRecord({
  roomId: 1,
  userId: 2,
  subject: '已结束的学术研讨会',
  attendees: 40,
  contact: '李同学',
  startTime: `${yesterday}T14:00:00`,
  endTime: `${yesterday}T15:00:00`,
  createdAt: `${yesterday}T09:00:00`
})

function issueToken(userId) {
  const token = crypto.randomBytes(32).toString('hex')
  tokens.set(token, {
    userId,
    expiresAt: Date.now() + TOKEN_TTL_SECONDS * 1000
  })
  return token
}

function revokeUserTokens(userId) {
  for (const [token, record] of tokens) {
    if (record.userId === userId) tokens.delete(token)
  }
}

function bearerToken(req) {
  const header = req.headers.authorization || ''
  const match = /^Bearer\s+(.+)$/i.exec(header)
  return match ? match[1].trim() : ''
}

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
  res.setHeader('Access-Control-Max-Age', '600')
}

function sendJson(res, status, payload) {
  setCors(res)
  const body = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body)
  })
  res.end(body)
}

function sendOk(res, data = null, message = '操作成功') {
  sendJson(res, 200, { code: 0, message, data })
}

function sendError(res, status, message, code = status * 100, data = null) {
  sendJson(res, status, { code, message, data })
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let size = 0

    req.on('data', (chunk) => {
      size += chunk.length
      if (size > 1_000_000) {
        const error = new Error('请求内容过大')
        error.status = 413
        reject(error)
        req.destroy()
        return
      }
      chunks.push(chunk)
    })

    req.on('end', () => {
      if (chunks.length === 0) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))
      } catch {
        const error = new Error('请求格式不正确')
        error.status = 400
        reject(error)
      }
    })

    req.on('error', reject)
  })
}

function authenticate(req, res) {
  const token = bearerToken(req)
  if (!token) {
    sendError(res, 401, '登录已过期，请重新登录', 40101)
    return null
  }

  const record = tokens.get(token)
  if (!record) {
    sendError(res, 401, '登录已过期，请重新登录', 40101)
    return null
  }

  if (record.expiresAt <= Date.now()) {
    tokens.delete(token)
    sendError(res, 401, '登录已过期，请重新登录', 40101)
    return null
  }

  const user = users.find((item) => item.id === record.userId)
  if (!user) {
    tokens.delete(token)
    sendError(res, 401, '登录状态已失效，请重新登录', 40102)
    return null
  }

  if (user.status === 'DISABLED') {
    revokeUserTokens(user.id)
    sendError(res, 401, '账号已停用，会话已失效', 40103)
    return null
  }

  return { user, token }
}

function requireAdmin(req, res) {
  const auth = authenticate(req, res)
  if (!auth) return null
  if (auth.user.role !== 'ADMIN') {
    sendError(res, 403, '无权访问管理功能', 40304)
    return null
  }
  return auth
}

function normalizeDateTime(value) {
  if (typeof value !== 'string') return ''
  const normalized = value.trim().replace(' ', 'T')
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(normalized)) return ''
  return normalized.length === 16 ? `${normalized}:00` : normalized
}

function parseLocalDateTime(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(value)
  if (!match) return null

  const date = new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4]),
    Number(match[5]),
    Number(match[6] || 0)
  )

  return Number.isNaN(date.getTime()) ? null : date
}

function toMilliseconds(value) {
  const date = parseLocalDateTime(value)
  return date ? date.getTime() : Number.NaN
}

function isValidDateString(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00`)
  return !Number.isNaN(date.getTime()) && toDateString(date) === value
}

/**
 * FR-09：半开区间 [start, end) 判定。
 * 只有 start_new < end_old AND end_new > start_old 才算冲突；
 * 首尾相接（end_new == start_old 或 start_new == end_old）不冲突。
 */
function hasOverlap(startNew, endNew, startOld, endOld) {
  return startNew < endOld && endNew > startOld
}

/** 占用查询与冲突检测共用这一处数据源（周计划 §3.1 硬要求）。 */
function getOccupiedSlots(roomId, date, excludeId = null) {
  return reservations
    .filter(
      (record) =>
        record.roomId === roomId &&
        !record.cancelled &&
        record.id !== excludeId &&
        record.startTime.slice(0, 10) === date
    )
    .sort((left, right) => left.startTime.localeCompare(right.startTime))
    .map((record) => ({
      startTime: record.startTime,
      endTime: record.endTime
    }))
}

function findConflicts(roomId, date, startMs, endMs, excludeId = null) {
  return getOccupiedSlots(roomId, date, excludeId).filter((slot) =>
    hasOverlap(startMs, endMs, toMilliseconds(slot.startTime), toMilliseconds(slot.endTime))
  )
}

function minutesOfDay(value) {
  const date = parseLocalDateTime(value)
  return date ? date.getHours() * 60 + date.getMinutes() : Number.NaN
}

function minutesToTime(minutes) {
  return `${pad2(Math.floor(minutes / 60))}:${pad2(minutes % 60)}`
}

function computeFreeSlots(date, occupiedSlots) {
  const occupied = occupiedSlots
    .map((slot) => ({
      start: minutesOfDay(slot.startTime),
      end: minutesOfDay(slot.endTime)
    }))
    .filter((slot) => Number.isFinite(slot.start) && Number.isFinite(slot.end))
    .sort((left, right) => left.start - right.start)

  const freeSlots = []
  let cursor = BUSINESS_START_MINUTES

  for (const slot of occupied) {
    if (slot.end <= BUSINESS_START_MINUTES || slot.start >= BUSINESS_END_MINUTES) {
      continue
    }

    const start = Math.max(slot.start, BUSINESS_START_MINUTES)
    const end = Math.min(slot.end, BUSINESS_END_MINUTES)

    if (start > cursor) {
      freeSlots.push({
        startTime: `${date}T${minutesToTime(cursor)}:00`,
        endTime: `${date}T${minutesToTime(start)}:00`
      })
    }

    cursor = Math.max(cursor, end)
  }

  if (cursor < BUSINESS_END_MINUTES) {
    freeSlots.push({
      startTime: `${date}T${minutesToTime(cursor)}:00`,
      endTime: `${date}T${minutesToTime(BUSINESS_END_MINUTES)}:00`
    })
  }

  return freeSlots
}

function normalizeEquipment(value) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean)
  }
  if (typeof value === 'string') {
    return value
      .split(/[,，、]/)
      .map((item) => item.trim())
      .filter(Boolean)
  }
  return []
}

function addOperationLog({
  operator,
  operationType,
  targetType,
  targetId,
  summary,
  beforeData = null,
  afterData = null
}) {
  logSequence += 1
  const log = {
    id: logSequence,
    operatorId: operator.id,
    operatorUsername: operator.username,
    operatorName: operator.name,
    operationType,
    targetType,
    targetId,
    summary,
    beforeData,
    afterData,
    createdAt: nowLocalString()
  }
  operationLogs.unshift(log)
  return log
}

function validateBookingPayload(body, { fallbackRoomId = null } = {}) {
  const roomIdRaw = body.roomId === undefined ? fallbackRoomId : Number(body.roomId)
  const subject = typeof body.subject === 'string' ? body.subject.trim() : ''
  const attendees = Number(body.attendees)
  const contact = typeof body.contact === 'string' ? body.contact.trim() : ''
  const startTime = normalizeDateTime(body.startTime)
  const endTime = normalizeDateTime(body.endTime)

  const missing = []
  if (!Number.isInteger(roomIdRaw) || roomIdRaw <= 0) missing.push('会议室')
  if (!subject) missing.push('会议主题')
  if (!Number.isInteger(attendees) || attendees <= 0) missing.push('参会人数')
  if (!contact) missing.push('联系人')
  if (!startTime) missing.push('开始时间')
  if (!endTime) missing.push('结束时间')

  if (missing.length > 0) {
    return { error: { status: 400, message: `请补充以下必填项：${missing.join('、')}`, code: 40020 } }
  }

  if (subject.length > 100) {
    return { error: { status: 400, message: '会议主题不能超过 100 个字符', code: 40026 } }
  }

  if (contact.length > 30) {
    return { error: { status: 400, message: '联系人不能超过 30 个字符', code: 40027 } }
  }

  const startMs = toMilliseconds(startTime)
  const endMs = toMilliseconds(endTime)

  if (!Number.isFinite(startMs) || !Number.isFinite(endMs)) {
    return { error: { status: 400, message: '时间格式不正确', code: 40022 } }
  }

  if (endMs <= startMs) {
    return { error: { status: 400, message: '结束时间必须晚于开始时间', code: 40023 } }
  }

  if (startTime.slice(0, 10) !== endTime.slice(0, 10)) {
    return { error: { status: 400, message: '单次预约不能跨天', code: 40024 } }
  }

  if (endMs - startMs > MAX_BOOKING_HOURS * 60 * 60 * 1000) {
    return { error: { status: 400, message: '单次预约不能超过 8 小时', code: 40025 } }
  }

  return {
    data: {
      roomId: roomIdRaw,
      subject,
      attendees,
      contact,
      startTime,
      endTime,
      startMs,
      endMs
    }
  }
}

async function handleLogin(req, res) {
  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const username = typeof body.username === 'string' ? body.username.trim() : ''
  const password = typeof body.password === 'string' ? body.password : ''

  if (!username || !password) {
    sendError(res, 400, '请输入账号和密码', 40001)
    return
  }

  const user = users.find((item) => item.username === username)
  if (!user || !verifyPassword(password, user.passwordHash)) {
    sendError(res, 401, '账号或密码错误', 40110)
    return
  }

  if (user.status === 'DISABLED') {
    sendError(res, 403, '账号已停用，请联系管理员', 40301)
    return
  }

  const token = issueToken(user.id)
  sendOk(
    res,
    {
      token,
      expiresIn: TOKEN_TTL_SECONDS,
      user: toUserVO(user)
    },
    '登录成功'
  )
}

function handleLogout(req, res) {
  const auth = authenticate(req, res)
  if (!auth) return

  tokens.delete(auth.token)
  sendOk(res, null, '已退出登录')
}

function handleRooms(req, res, url) {
  const auth = authenticate(req, res)
  if (!auth) return

  const name = (url.searchParams.get('name') || '').trim().toLowerCase()
  const location = (url.searchParams.get('location') || '').trim().toLowerCase()
  const minCapacityRaw = url.searchParams.get('minCapacity')

  let minCapacity = 0
  if (minCapacityRaw !== null) {
    if (!/^\d+$/.test(minCapacityRaw) || Number(minCapacityRaw) <= 0) {
      sendError(res, 400, '容量筛选条件不正确', 40010)
      return
    }
    minCapacity = Number(minCapacityRaw)
  }

  const result = rooms.filter((room) => {
    if (name && !room.name.toLowerCase().includes(name)) return false
    if (location && !room.location.toLowerCase().includes(location)) return false
    if (minCapacity && room.capacity < minCapacity) return false
    return true
  })

  sendOk(res, result)
}

function handleOccupancy(req, res, url, roomId) {
  const auth = authenticate(req, res)
  if (!auth) return

  const room = rooms.find((item) => item.id === roomId)
  if (!room) {
    sendError(res, 404, '未找到该会议室', 40401)
    return
  }

  const date = (url.searchParams.get('date') || '').trim()
  if (!isValidDateString(date)) {
    sendError(res, 400, '请选择正确的日期', 40011)
    return
  }

  const occupiedSlots = getOccupiedSlots(roomId, date)
  const freeSlots = computeFreeSlots(date, occupiedSlots)

  sendOk(res, {
    roomId,
    roomName: room.name,
    occupiedSlots,
    freeSlots
  })
}

async function handleCreateBooking(req, res) {
  const auth = authenticate(req, res)
  if (!auth) return

  if (auth.user.role !== 'USER') {
    sendError(res, 403, '管理员账号不允许提交预约', 40302)
    return
  }

  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const parsed = validateBookingPayload(body)
  if (parsed.error) {
    sendError(res, parsed.error.status, parsed.error.message, parsed.error.code)
    return
  }

  const { roomId, startTime, endTime, startMs, endMs, subject, attendees, contact } = parsed.data
  const room = rooms.find((item) => item.id === roomId)
  if (!room) {
    sendError(res, 404, '未找到该会议室', 40401)
    return
  }

  if (room.status !== 'AVAILABLE') {
    sendError(res, 400, '该会议室已停用，无法提交预约', 40021)
    return
  }

  const conflicts = findConflicts(roomId, startTime.slice(0, 10), startMs, endMs)
  if (conflicts.length > 0) {
    sendError(res, 409, '该时段已被占用，请选择其他时间', 40901, {
      occupiedSlots: conflicts
    })
    return
  }

  const record = createReservationRecord({
    roomId,
    userId: auth.user.id,
    subject,
    attendees,
    contact,
    startTime,
    endTime
  })

  sendOk(res, toBookingVO(record), '预约成功')
}

function handleMyBookings(req, res) {
  const auth = authenticate(req, res)
  if (!auth) return

  if (auth.user.role !== 'USER') {
    sendError(res, 403, '管理员账号无个人预约', 40303)
    return
  }

  const result = reservations
    .filter((record) => record.userId === auth.user.id)
    .sort((left, right) => {
      const rank = { PENDING: 0, ONGOING: 1, FINISHED: 2, CANCELLED: 3 }
      const leftRank = rank[deriveStatus(left)]
      const rightRank = rank[deriveStatus(right)]
      if (leftRank !== rightRank) return leftRank - rightRank
      return right.startTime.localeCompare(left.startTime)
    })
    .map(toBookingVO)

  sendOk(res, result)
}

function getOwnedBooking(req, res, bookingId) {
  const auth = authenticate(req, res)
  if (!auth) return null

  if (auth.user.role !== 'USER') {
    sendError(res, 403, '管理员账号无个人预约', 40303)
    return null
  }

  const record = reservations.find((item) => item.id === bookingId)
  if (!record) {
    sendError(res, 404, '未找到该预约', 40402)
    return null
  }

  if (record.userId !== auth.user.id) {
    sendError(res, 403, '无权操作他人的预约', 40305)
    return null
  }

  return { auth, record }
}

function checkOperable(record, actionName) {
  const status = deriveStatus(record)
  if (status === 'CANCELLED') {
    return `该预约已取消，无法${actionName}`
  }
  if (status !== 'PENDING') {
    return `会议已开始或已结束，无法${actionName}`
  }

  const startMs = toMilliseconds(record.startTime)
  if (Number.isFinite(startMs) && startMs - Date.now() <= MODIFY_CUTOFF_MINUTES * 60 * 1000) {
    return `距会议开始不足 ${MODIFY_CUTOFF_MINUTES} 分钟，无法${actionName}`
  }

  return ''
}

async function handleUpdateBooking(req, res, bookingId) {
  const owned = getOwnedBooking(req, res, bookingId)
  if (!owned) return

  const { record } = owned
  const blockedReason = checkOperable(record, '修改预约')
  if (blockedReason) {
    sendError(res, 400, blockedReason, 40030)
    return
  }

  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const parsed = validateBookingPayload(body, { fallbackRoomId: record.roomId })
  if (parsed.error) {
    sendError(res, parsed.error.status, parsed.error.message, parsed.error.code)
    return
  }

  const { roomId, startTime, endTime, startMs, endMs, subject, attendees, contact } = parsed.data
  const room = rooms.find((item) => item.id === roomId)
  if (!room) {
    sendError(res, 404, '未找到该会议室', 40401)
    return
  }

  if (room.status !== 'AVAILABLE') {
    sendError(res, 400, '该会议室已停用，无法预约', 40021)
    return
  }

  const conflicts = findConflicts(roomId, startTime.slice(0, 10), startMs, endMs, record.id)
  if (conflicts.length > 0) {
    sendError(res, 409, '该时段已被占用，请选择其他时间', 40901, {
      occupiedSlots: conflicts
    })
    return
  }

  record.roomId = roomId
  record.subject = subject
  record.attendees = attendees
  record.contact = contact
  record.startTime = startTime
  record.endTime = endTime

  sendOk(res, toBookingVO(record), '预约已修改')
}

function handleCancelBooking(req, res, bookingId) {
  const owned = getOwnedBooking(req, res, bookingId)
  if (!owned) return

  const { auth, record } = owned
  const blockedReason = checkOperable(record, '取消预约')
  if (blockedReason) {
    sendError(res, 400, blockedReason, 40031)
    return
  }

  record.cancelled = true
  record.cancelReason = '用户主动取消'
  record.cancelledBy = auth.user.username
  record.cancelledAt = nowLocalString()

  sendOk(res, toBookingVO(record), '预约已取消，时段已释放')
}

function handleAdminBookings(req, res, url) {
  const auth = requireAdmin(req, res)
  if (!auth) return

  const roomIdRaw = url.searchParams.get('roomId')
  const date = (url.searchParams.get('date') || '').trim()

  let roomId = null
  if (roomIdRaw !== null) {
    if (!/^\d+$/.test(roomIdRaw) || Number(roomIdRaw) <= 0) {
      sendError(res, 400, '会议室筛选条件不正确', 40040)
      return
    }
    roomId = Number(roomIdRaw)
  }

  if (date && !isValidDateString(date)) {
    sendError(res, 400, '日期筛选条件不正确', 40041)
    return
  }

  const result = reservations
    .filter((record) => {
      if (roomId !== null && record.roomId !== roomId) return false
      if (date && record.startTime.slice(0, 10) !== date) return false
      return true
    })
    .sort((left, right) => right.startTime.localeCompare(left.startTime))
    .map(toAdminBookingVO)

  sendOk(res, result)
}

async function handleForceCancel(req, res, bookingId) {
  const auth = requireAdmin(req, res)
  if (!auth) return

  const record = reservations.find((item) => item.id === bookingId)
  if (!record) {
    sendError(res, 404, '未找到该预约', 40402)
    return
  }

  if (record.cancelled) {
    sendError(res, 400, '该预约已取消，无需重复处理', 40032)
    return
  }

  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const reason = typeof body.reason === 'string' ? body.reason.trim() : ''
  if (!reason) {
    sendError(res, 400, '请填写取消原因', 40033)
    return
  }

  if (reason.length > 200) {
    sendError(res, 400, '取消原因不能超过 200 个字符', 40034)
    return
  }

  const before = toAdminBookingVO(record)
  record.cancelled = true
  record.cancelReason = reason
  record.cancelledBy = auth.user.username
  record.cancelledAt = nowLocalString()
  const after = toAdminBookingVO(record)

  addOperationLog({
    operator: auth.user,
    operationType: 'FORCE_CANCEL',
    targetType: 'BOOKING',
    targetId: record.id,
    summary: `强制取消预约 ${record.reservationNo}（原因：${reason}）`,
    beforeData: before,
    afterData: after
  })

  sendOk(res, after, '预约已强制取消')
}

function validateRoomPayload(body, currentRoom = null) {
  const name = typeof body.name === 'string' ? body.name.trim() : ''
  const location = typeof body.location === 'string' ? body.location.trim() : ''
  const capacity = Number(body.capacity)
  const equipment = normalizeEquipment(body.equipment)
  const status = body.status === undefined ? currentRoom?.status || 'AVAILABLE' : body.status

  const missing = []
  if (!name) missing.push('会议室名称')
  if (!location) missing.push('位置')
  if (!Number.isInteger(capacity) || capacity <= 0) missing.push('容量')

  if (missing.length > 0) {
    return { error: { status: 400, message: `请补充以下必填项：${missing.join('、')}`, code: 40050 } }
  }

  if (name.length > 50) {
    return { error: { status: 400, message: '会议室名称不能超过 50 个字符', code: 40051 } }
  }

  if (!['AVAILABLE', 'DISABLED'].includes(status)) {
    return { error: { status: 400, message: '会议室状态不正确', code: 40052 } }
  }

  const duplicate = rooms.find(
    (room) => room.name === name && (!currentRoom || room.id !== currentRoom.id)
  )
  if (duplicate) {
    return { error: { status: 409, message: '会议室名称已存在，请换一个名称', code: 40910 } }
  }

  return { data: { name, location, capacity, equipment, status } }
}

function handleAdminRooms(req, res) {
  const auth = requireAdmin(req, res)
  if (!auth) return
  sendOk(res, [...rooms].sort((left, right) => left.id - right.id))
}

async function handleCreateRoom(req, res) {
  const auth = requireAdmin(req, res)
  if (!auth) return

  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const parsed = validateRoomPayload(body)
  if (parsed.error) {
    sendError(res, parsed.error.status, parsed.error.message, parsed.error.code)
    return
  }

  roomSequence += 1
  const room = { id: roomSequence, ...parsed.data }
  rooms.push(room)

  addOperationLog({
    operator: auth.user,
    operationType: 'CREATE_ROOM',
    targetType: 'ROOM',
    targetId: room.id,
    summary: `新增会议室「${room.name}」`,
    beforeData: null,
    afterData: { ...room }
  })

  sendOk(res, room, '会议室已新增')
}

async function handleUpdateRoom(req, res, roomId) {
  const auth = requireAdmin(req, res)
  if (!auth) return

  const room = rooms.find((item) => item.id === roomId)
  if (!room) {
    sendError(res, 404, '未找到该会议室', 40401)
    return
  }

  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const parsed = validateRoomPayload(body, room)
  if (parsed.error) {
    sendError(res, parsed.error.status, parsed.error.message, parsed.error.code)
    return
  }

  const before = { ...room }
  Object.assign(room, parsed.data)

  addOperationLog({
    operator: auth.user,
    operationType: 'UPDATE_ROOM',
    targetType: 'ROOM',
    targetId: room.id,
    summary: `修改会议室「${room.name}」`,
    beforeData: before,
    afterData: { ...room }
  })

  sendOk(res, room, '会议室已更新')
}

function handleDeleteRoom(req, res, roomId) {
  const auth = requireAdmin(req, res)
  if (!auth) return

  const room = rooms.find((item) => item.id === roomId)
  if (!room) {
    sendError(res, 404, '未找到该会议室', 40401)
    return
  }

  const activeBooking = reservations.some(
    (record) => record.roomId === roomId && !record.cancelled
  )
  if (activeBooking) {
    sendError(res, 409, '该会议室存在有效预约，请先处理预约', 40911)
    return
  }

  const index = rooms.findIndex((item) => item.id === roomId)
  rooms.splice(index, 1)

  addOperationLog({
    operator: auth.user,
    operationType: 'DELETE_ROOM',
    targetType: 'ROOM',
    targetId: room.id,
    summary: `删除会议室「${room.name}」`,
    beforeData: { ...room },
    afterData: null
  })

  sendOk(res, null, '会议室已删除')
}

function handleAdminUsers(req, res) {
  const auth = requireAdmin(req, res)
  if (!auth) return
  sendOk(
    res,
    [...users].sort((left, right) => left.id - right.id).map(toAdminUserVO)
  )
}

async function handleCreateUser(req, res) {
  const auth = requireAdmin(req, res)
  if (!auth) return

  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const username = typeof body.username === 'string' ? body.username.trim() : ''
  const name = typeof body.name === 'string' ? body.name.trim() : ''
  const department = typeof body.department === 'string' ? body.department.trim() : ''
  const role = typeof body.role === 'string' ? body.role : 'USER'
  const password = typeof body.password === 'string' ? body.password : ''

  if (!username || !name || !password) {
    sendError(res, 400, '请填写账号、姓名和初始密码', 40060)
    return
  }

  if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
    sendError(res, 400, '账号需为 3-20 位字母、数字或下划线', 40061)
    return
  }

  if (users.some((item) => item.username === username)) {
    sendError(res, 409, '该账号已存在，请换一个账号', 40920)
    return
  }

  if (!['USER', 'ADMIN'].includes(role)) {
    sendError(res, 400, '用户角色不正确', 40062)
    return
  }

  if (password.length < 6) {
    sendError(res, 400, '初始密码至少 6 位', 40063)
    return
  }

  userSequence += 1
  const user = {
    id: userSequence,
    username,
    name,
    department,
    role,
    status: 'ACTIVE',
    passwordHash: hashPassword(password),
    createdAt: nowLocalString()
  }
  users.push(user)

  addOperationLog({
    operator: auth.user,
    operationType: 'CREATE_USER',
    targetType: 'USER',
    targetId: user.id,
    summary: `新增用户「${user.username}」`,
    beforeData: null,
    afterData: toAdminUserSnapshot(user)
  })

  sendOk(res, toAdminUserVO(user), '用户已新增')
}

async function handleUpdateUser(req, res, userId) {
  const auth = requireAdmin(req, res)
  if (!auth) return

  const user = users.find((item) => item.id === userId)
  if (!user) {
    sendError(res, 404, '未找到该用户', 40403)
    return
  }

  let body
  try {
    body = await readJson(req)
  } catch (error) {
    sendError(res, error.status || 400, error.message || '请求格式不正确', 40000)
    return
  }

  const before = toAdminUserSnapshot(user)

  if (body.name !== undefined) {
    const name = typeof body.name === 'string' ? body.name.trim() : ''
    if (!name) {
      sendError(res, 400, '姓名不能为空', 40064)
      return
    }
    user.name = name
  }

  if (body.department !== undefined) {
    user.department = typeof body.department === 'string' ? body.department.trim() : ''
  }

  if (body.role !== undefined) {
    if (!['USER', 'ADMIN'].includes(body.role)) {
      sendError(res, 400, '用户角色不正确', 40062)
      return
    }
    if (user.id === auth.user.id && body.role !== user.role) {
      sendError(res, 400, '不能修改当前登录账号的角色', 40065)
      return
    }
    user.role = body.role
  }

  if (body.status !== undefined) {
    if (!['ACTIVE', 'DISABLED'].includes(body.status)) {
      sendError(res, 400, '用户状态不正确', 40066)
      return
    }
    if (user.id === auth.user.id && body.status === 'DISABLED') {
      sendError(res, 400, '不能停用当前登录账号', 40067)
      return
    }
    user.status = body.status
    if (body.status === 'DISABLED') revokeUserTokens(user.id)
  }

  const after = toAdminUserSnapshot(user)
  const roleChanged = before.role !== after.role
  const statusChanged = before.status !== after.status

  addOperationLog({
    operator: auth.user,
    operationType: roleChanged ? 'ROLE_CHANGE' : 'UPDATE_USER',
    targetType: 'USER',
    targetId: user.id,
    summary: roleChanged
      ? `将用户「${user.username}」角色由 ${before.role} 变更为 ${after.role}`
      : statusChanged
        ? `将用户「${user.username}」状态变更为 ${after.status}`
        : `修改用户「${user.username}」信息`,
    beforeData: before,
    afterData: after
  })

  sendOk(res, toAdminUserVO(user), '用户已更新')
}

function handleAdminLogs(req, res) {
  const auth = requireAdmin(req, res)
  if (!auth) return
  sendOk(res, operationLogs)
}

async function handleRequest(req, res, url) {
  const { pathname } = url

  if (req.method === 'OPTIONS') {
    setCors(res)
    res.writeHead(204)
    res.end()
    return
  }

  if (pathname === '/api/health') {
    if (req.method !== 'GET') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    sendOk(res, { status: 'ok', time: nowLocalString() })
    return
  }

  if (pathname === '/api/auth/login') {
    if (req.method !== 'POST') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    await handleLogin(req, res)
    return
  }

  if (pathname === '/api/auth/logout') {
    if (req.method !== 'POST') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    handleLogout(req, res)
    return
  }

  const occupancyMatch = /^\/api\/rooms\/(\d+)\/occupancy$/.exec(pathname)
  if (occupancyMatch) {
    if (req.method !== 'GET') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    handleOccupancy(req, res, url, Number(occupancyMatch[1]))
    return
  }

  if (pathname === '/api/rooms') {
    if (req.method !== 'GET') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    handleRooms(req, res, url)
    return
  }

  if (pathname === '/api/bookings' && req.method === 'POST') {
    await handleCreateBooking(req, res)
    return
  }

  if (pathname === '/api/bookings/mine') {
    if (req.method !== 'GET') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    handleMyBookings(req, res)
    return
  }

  const bookingMatch = /^\/api\/bookings\/(\d+)$/.exec(pathname)
  if (bookingMatch) {
    const bookingId = Number(bookingMatch[1])
    if (req.method === 'PUT') {
      await handleUpdateBooking(req, res, bookingId)
      return
    }
    if (req.method === 'DELETE') {
      handleCancelBooking(req, res, bookingId)
      return
    }
    sendError(res, 405, '请求方法不被允许', 40501)
    return
  }

  if (pathname === '/api/admin/bookings') {
    if (req.method !== 'GET') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    handleAdminBookings(req, res, url)
    return
  }

  const forceCancelMatch = /^\/api\/admin\/bookings\/(\d+)\/cancel$/.exec(pathname)
  if (forceCancelMatch) {
    if (req.method !== 'PUT') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    await handleForceCancel(req, res, Number(forceCancelMatch[1]))
    return
  }

  if (pathname === '/api/admin/rooms') {
    if (req.method === 'GET') {
      handleAdminRooms(req, res)
      return
    }
    if (req.method === 'POST') {
      await handleCreateRoom(req, res)
      return
    }
    sendError(res, 405, '请求方法不被允许', 40501)
    return
  }

  const adminRoomMatch = /^\/api\/admin\/rooms\/(\d+)$/.exec(pathname)
  if (adminRoomMatch) {
    const roomId = Number(adminRoomMatch[1])
    if (req.method === 'PUT') {
      await handleUpdateRoom(req, res, roomId)
      return
    }
    if (req.method === 'DELETE') {
      handleDeleteRoom(req, res, roomId)
      return
    }
    sendError(res, 405, '请求方法不被允许', 40501)
    return
  }

  if (pathname === '/api/admin/users') {
    if (req.method === 'GET') {
      handleAdminUsers(req, res)
      return
    }
    if (req.method === 'POST') {
      await handleCreateUser(req, res)
      return
    }
    sendError(res, 405, '请求方法不被允许', 40501)
    return
  }

  const adminUserMatch = /^\/api\/admin\/users\/(\d+)$/.exec(pathname)
  if (adminUserMatch) {
    if (req.method !== 'PUT') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    await handleUpdateUser(req, res, Number(adminUserMatch[1]))
    return
  }

  if (pathname === '/api/admin/logs') {
    if (req.method !== 'GET') {
      sendError(res, 405, '请求方法不被允许', 40501)
      return
    }
    handleAdminLogs(req, res)
    return
  }

  if (pathname.startsWith('/api/admin/')) {
    const auth = requireAdmin(req, res)
    if (!auth) return
    sendError(res, 404, '接口不存在，请检查请求地址', 40400)
    return
  }

  sendError(res, 404, '接口不存在，请检查请求地址', 40400)
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)

  res.on('finish', () => {
    console.log(`${req.method} ${url.pathname}${url.search} -> ${res.statusCode}`)
  })

  handleRequest(req, res, url).catch((error) => {
    console.error('[mock] 未处理异常:', error)
    if (!res.headersSent) {
      sendError(res, 500, '服务器开小差了，请稍后重试', 50000)
    }
  })
})

server.listen(PORT, () => {
  console.log('')
  console.log('智能会议室管理系统 · 第11周本地 mock 后端')
  console.log(`  API: http://localhost:${PORT}/api`)
  console.log(`  令牌有效期: ${TOKEN_TTL_SECONDS} 秒`)
  console.log('  演示账号（仅本地 mock，不是生产账号）：')
  if (QUIET_CREDENTIALS) {
    console.log('    口令由启动参数注入，本次运行不在日志中输出')
  } else {
    console.log(`    普通用户 user001 / ${USER_PASSWORD}`)
    console.log(`    管理员   admin001 / ${ADMIN_PASSWORD}`)
    console.log(`    停用样例 disabled001 / ${USER_PASSWORD}`)
  }
  console.log(`  种子预约：${tomorrow} 学术报告厅 10:00-11:00、3号楼301 14:00-16:00`)
  console.log(`            ${dayAfterTomorrow} 行政楼小会议室 09:00-10:00`)
  console.log(`            ${yesterday} 学术报告厅 14:00-15:00（已结束，供管理员强制取消演示）`)
  console.log('  按 Ctrl+C 停止。数据仅存内存，重启即重置。')
  console.log('')
})

function shutdown() {
  server.close(() => process.exit(0))
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
