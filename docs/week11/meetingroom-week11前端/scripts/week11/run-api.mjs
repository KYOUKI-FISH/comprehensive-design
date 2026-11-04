import crypto from 'node:crypto'
import path from 'node:path'
import process from 'node:process'
import {
  EVIDENCE_DIR,
  addDays,
  apiRequest,
  assertLocalBaseUrl,
  createChecks,
  dayAfterTomorrowString,
  isHealthy,
  login,
  nowLocalString,
  renderRequest,
  renderResponse,
  startMock,
  toDateString,
  tomorrowString,
  writeJson,
  writeText
} from './lib.mjs'

/**
 * 第 11 周孙若普负责的 API 侧测试：
 * - TC-07 普通用户调管理接口
 * - TC-08 普通用户查操作日志
 * - TC-12 我的预约只返回本人记录
 * - 越权取消 / 越权查看 / 时间边界 三类攻击，每类三样证据
 *
 * 安全边界：只允许 localhost / 127.0.0.1，拒绝任何外网地址。
 */

const baseUrl = (process.env.WEEK11_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '')
const base = assertLocalBaseUrl(baseUrl)
const checks = createChecks()
const startedAt = nowLocalString()

const tomorrow = tomorrowString()
const dayAfter = dayAfterTomorrowString()

function randomPassword() {
  return crypto.randomBytes(12).toString('base64url')
}

function bookingBrief(booking) {
  if (!booking) return null
  return {
    id: booking.id,
    reservationNo: booking.reservationNo,
    roomId: booking.roomId,
    roomName: booking.roomName,
    userId: booking.userId,
    username: booking.username,
    subject: booking.subject,
    startTime: booking.startTime,
    endTime: booking.endTime,
    status: booking.status,
    cancelledAt: booking.cancelledAt || '',
    cancelReason: booking.cancelReason || ''
  }
}

function findById(list, id) {
  return list.find((item) => item.id === id)
}

function timeToMinutes(value) {
  return Number(value.slice(11, 13)) * 60 + Number(value.slice(14, 16))
}

function minutesToTime(minutes) {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}:00`
}

function containsAdminData(payload) {
  const text = JSON.stringify(payload)
  return [
    'reservationNo',
    'operationType',
    'operatorUsername',
    '课题组周会',
    '学院工作例会'
  ].some((marker) => text.includes(marker))
}

async function getJson(apiPath, token) {
  const response = await apiRequest(baseUrl, { apiPath, token })
  if (response.status !== 200) {
    throw new Error(`GET ${apiPath} 失败：status=${response.status} message=${response.responseBody?.message || '-'}`)
  }
  return response.responseBody.data
}

async function main() {
  let mock = null
  let userPassword = process.env.WEEK11_USER_PASSWORD || ''
  let adminPassword = process.env.WEEK11_ADMIN_PASSWORD || ''

  const serviceAlreadyRunning = await isHealthy(baseUrl)
  if (!serviceAlreadyRunning) {
    userPassword = userPassword || randomPassword()
    adminPassword = adminPassword || randomPassword()
    mock = await startMock({
      port: Number(base.port || 80),
      userPassword,
      adminPassword,
      logName: 'week11-api-mock.log'
    })
  } else if (!userPassword || !adminPassword) {
    throw new Error(
      '检测到已有服务在运行：请通过 WEEK11_USER_PASSWORD / WEEK11_ADMIN_PASSWORD 提供测试口令，' +
      '脚本不会猜测或写入任何口令。'
    )
  }

  try {
    const user1 = await login(baseUrl, 'user001', userPassword)
    const user2 = await login(baseUrl, 'user002', userPassword)
    const admin = await login(baseUrl, 'admin001', adminPassword)

    const adminBookingsBefore = await getJson('/api/admin/bookings', admin.token)
    const logsBefore = await getJson('/api/admin/logs', admin.token)
    const user1MineBefore = await getJson('/api/bookings/mine', user1.token)
    const user2MineBefore = await getJson('/api/bookings/mine', user2.token)
    const rooms = await getJson('/api/rooms', user1.token)

    // ---------- 选定被攻击的预约：优先挑 user001 的待开始预约 ----------
    const target = adminBookingsBefore.find(
      (item) => item.username === 'user001' && item.status === 'PENDING'
    ) || adminBookingsBefore.find((item) => item.status === 'PENDING')

    if (!target) {
      throw new Error('库中没有待开始的预约，无法执行越权取消用例；请先准备测试数据。')
    }

    checks.check('TC-07-prep', '准备：找到被攻击的预约', true, `id=${target.id} ${target.subject}`)

    // ---------- 攻击 1：越权取消 ----------
    const cancelAttempt = await apiRequest(baseUrl, {
      method: 'DELETE',
      apiPath: `/api/bookings/${target.id}`,
      token: user2.token
    })
    const adminBookingsAfterCancel = await getJson('/api/admin/bookings', admin.token)
    const targetAfterCancel = findById(adminBookingsAfterCancel, target.id)

    const cancelBlocked = cancelAttempt.status === 403
    const cancelMessageOk = String(cancelAttempt.responseBody?.message || '').includes('无权操作他人的预约')
    const cancelDataEmpty = cancelAttempt.responseBody?.data === null || cancelAttempt.responseBody?.data === undefined
    const targetUnchanged = Boolean(
      targetAfterCancel &&
      targetAfterCancel.status === target.status &&
      !targetAfterCancel.cancelledAt &&
      targetAfterCancel.startTime === target.startTime
    )

    checks.check('ATK-01', '越权取消返回 403', cancelBlocked, `status=${cancelAttempt.status}`)
    checks.check('ATK-01-msg', '越权取消返回中文业务提示', cancelMessageOk, cancelAttempt.responseBody?.message || '-')
    checks.check('ATK-01-data', '越权取消响应不含预约数据', cancelDataEmpty)
    checks.check('ATK-01-db', '被攻击预约仍在库且状态未变', targetUnchanged,
      `${targetAfterCancel?.status || '-'} / cancelledAt=${targetAfterCancel?.cancelledAt || '空'}`)

    await writeText(path.join('docs', 'week11', '取证', '01_越权取消', '01_请求.txt'),
      `${renderRequest(cancelAttempt)}`)
    await writeText(path.join('docs', 'week11', '取证', '01_越权取消', '02_响应.txt'),
      `${renderResponse(cancelAttempt)}`)
    await writeText(path.join('docs', 'week11', '取证', '01_越权取消', '03_库内数据.txt'), [
      `取证时间：${nowLocalString()}`,
      `目标预约：id=${target.id}，编号=${target.reservationNo}，主题=${target.subject}`,
      `攻击者：user002（令牌已脱敏）`,
      '',
      '--- 操作前（管理员接口回读同一数据源）---',
      JSON.stringify(bookingBrief(target), null, 2),
      '',
      '--- 操作后 ---',
      JSON.stringify(bookingBrief(targetAfterCancel), null, 2),
      '',
      `结论：${targetUnchanged ? '预约仍在库，状态未变，越权取消未执行。' : '预约数据发生变化，需立即排查。'}`,
      '',
      '说明：本目录没有 MySQL，mock 的数据源为内存数组，因此用管理员接口回读代替 SELECT。',
      '正式后端复跑时改用：SELECT id, reservation_no, status, cancelled_at FROM reservation WHERE id = ?;',
      ''
    ].join('\n'))
    await writeJson(path.join('docs', 'week11', '取证', '01_越权取消', 'result.json'), {
      executedAt: nowLocalString(),
      attacker: 'user002',
      target: bookingBrief(target),
      responseStatus: cancelAttempt.status,
      responseBody: cancelAttempt.responseBody,
      after: bookingBrief(targetAfterCancel),
      unchanged: targetUnchanged
    })

    // ---------- 攻击 2 / TC-07 / TC-08：越权查看 ----------
    const viewBookings = await apiRequest(baseUrl, {
      apiPath: '/api/admin/bookings',
      token: user1.token
    })
    const viewLogs = await apiRequest(baseUrl, {
      apiPath: '/api/admin/logs',
      token: user1.token
    })
    const adminBookingsAfterView = await getJson('/api/admin/bookings', admin.token)
    const logsAfterView = await getJson('/api/admin/logs', admin.token)

    const viewBookingsBlocked = viewBookings.status === 403
    const viewLogsBlocked = viewLogs.status === 403
    const viewBookingsNoData = !containsAdminData(viewBookings.responseBody)
    const viewLogsNoData = !containsAdminData(viewLogs.responseBody)
    const adminCountUnchanged = adminBookingsAfterView.length === adminBookingsBefore.length
    const logCountUnchanged = logsAfterView.length === logsBefore.length

    checks.check('TC-07', '普通用户调全部预约接口返回 403', viewBookingsBlocked, `status=${viewBookings.status}`)
    checks.check('TC-07-data', '普通用户调全部预约接口不返回数据', viewBookingsNoData)
    checks.check('TC-08', '普通用户查操作日志返回 403', viewLogsBlocked, `status=${viewLogs.status}`)
    checks.check('TC-08-data', '普通用户查操作日志不返回数据', viewLogsNoData)
    checks.check('ATK-02', '越权查看后管理数据条数未变', adminCountUnchanged && logCountUnchanged,
      `预约 ${adminBookingsBefore.length}→${adminBookingsAfterView.length}；日志 ${logsBefore.length}→${logsAfterView.length}`)

    await writeText(path.join('docs', 'week11', '取证', '02_越权查看', '01_请求.txt'),
      `${renderRequest(viewBookings)}\n${renderRequest(viewLogs)}`)
    await writeText(path.join('docs', 'week11', '取证', '02_越权查看', '02_响应.txt'),
      `${renderResponse(viewBookings)}\n${renderResponse(viewLogs)}`)
    await writeText(path.join('docs', 'week11', '取证', '02_越权查看', '03_库内数据.txt'), [
      `取证时间：${nowLocalString()}`,
      '攻击者：user001（普通用户，令牌已脱敏）',
      '',
      '--- 管理数据条数（管理员接口回读）---',
      `预约总数：操作前 ${adminBookingsBefore.length} 条 → 操作后 ${adminBookingsAfterView.length} 条`,
      `日志总数：操作前 ${logsBefore.length} 条 → 操作后 ${logsAfterView.length} 条`,
      '',
      '--- 普通用户响应体是否包含管理数据 ---',
      `全部预约接口：${viewBookingsNoData ? '未包含' : '包含（异常）'}`,
      `操作日志接口：${viewLogsNoData ? '未包含' : '包含（异常）'}`,
      '',
      `结论：${viewBookingsBlocked && viewLogsBlocked && viewBookingsNoData && viewLogsNoData
        ? '两个管理接口均返回 403，响应体未泄露他人预约或日志。'
        : '存在越权读取风险，需立即排查。'}`,
      '',
      '说明：本目录没有 MySQL，管理数据条数用管理员接口回读；正式后端复跑时改用 COUNT(*) 查询。',
      ''
    ].join('\n'))
    await writeJson(path.join('docs', 'week11', '取证', '02_越权查看', 'result.json'), {
      executedAt: nowLocalString(),
      attacker: 'user001',
      requests: [
        { path: '/api/admin/bookings', status: viewBookings.status, body: viewBookings.responseBody },
        { path: '/api/admin/logs', status: viewLogs.status, body: viewLogs.responseBody }
      ],
      before: { bookings: adminBookingsBefore.length, logs: logsBefore.length },
      after: { bookings: adminBookingsAfterView.length, logs: logsAfterView.length },
      leaked: { bookings: !viewBookingsNoData, logs: !viewLogsNoData }
    })

    // ---------- 攻击 3：时间边界 ----------
    const availableRooms = rooms.filter((room) => room.status === 'AVAILABLE')
    if (availableRooms.length === 0) {
      throw new Error('没有可用会议室，无法执行时间边界用例。')
    }

    const tomorrowActive = adminBookingsBefore
      .filter((item) => item.status !== 'CANCELLED' && item.startTime.startsWith(tomorrow))
      .sort((left, right) => left.startTime.localeCompare(right.startTime))

    const adjacencySeed = tomorrowActive.find((item) => {
      const endMinutes = timeToMinutes(item.endTime)
      return endMinutes < 22 * 60 && item.endTime.slice(0, 10) === tomorrow
    })

    if (!adjacencySeed) {
      throw new Error('明天没有可用的已占用时段，无法构造“首尾相接”用例。')
    }

    const adjacencyRoomId = adjacencySeed.roomId
    const adjacencyStart = adjacencySeed.endTime
    const adjacencyEnd = `${tomorrow}T${minutesToTime(timeToMinutes(adjacencySeed.endTime) + 60)}`

    // 非法时段用例要选一间明天与后天都没有预约的会议室，避免“被拒绝”被误读成冲突检测
    const occupiedRoomIds = new Set(
      adminBookingsBefore
        .filter((item) =>
          item.status !== 'CANCELLED' &&
          (item.startTime.startsWith(tomorrow) || item.startTime.startsWith(dayAfter))
        )
        .map((item) => item.roomId)
    )
    const freeRoom =
      availableRooms.find((room) => room.id !== adjacencyRoomId && !occupiedRoomIds.has(room.id)) ||
      availableRooms.find((room) => room.id !== adjacencyRoomId) ||
      availableRooms[0]

    const boundaryCases = [
      {
        key: '跨天',
        subject: 'W11-时间边界-跨天',
        roomId: freeRoom.id,
        startTime: `${tomorrow}T23:00:00`,
        endTime: `${dayAfter}T01:00:00`,
        expectStatus: 400,
        expectCode: 40024,
        expectMessage: '单次预约不能跨天'
      },
      {
        key: '超8小时',
        subject: 'W11-时间边界-超8小时',
        roomId: freeRoom.id,
        startTime: `${dayAfter}T08:00:00`,
        endTime: `${dayAfter}T17:00:00`,
        expectStatus: 400,
        expectCode: 40025,
        expectMessage: '单次预约不能超过 8 小时'
      },
      {
        key: '结束早于开始',
        subject: 'W11-时间边界-结束早于开始',
        roomId: freeRoom.id,
        startTime: `${dayAfter}T14:00:00`,
        endTime: `${dayAfter}T13:00:00`,
        expectStatus: 400,
        expectCode: 40023,
        expectMessage: '结束时间必须晚于开始时间'
      },
      {
        key: '首尾相接',
        subject: 'W11-时间边界-首尾相接',
        roomId: adjacencyRoomId,
        startTime: adjacencyStart,
        endTime: adjacencyEnd,
        expectStatus: 200,
        expectCode: 0,
        expectMessage: '预约成功'
      }
    ]

    const user1MineBeforeBoundary = await getJson('/api/bookings/mine', user1.token)
    const adminBookingsBeforeBoundary = await getJson('/api/admin/bookings', admin.token)
    const boundaryResults = []

    for (const item of boundaryCases) {
      const response = await apiRequest(baseUrl, {
        method: 'POST',
        apiPath: '/api/bookings',
        token: user1.token,
        body: {
          roomId: item.roomId,
          subject: item.subject,
          attendees: 6,
          contact: '张同学',
          startTime: item.startTime,
          endTime: item.endTime
        }
      })
      const okStatus = response.status === item.expectStatus
      const okCode = response.responseBody?.code === item.expectCode
      const okMessage = String(response.responseBody?.message || '').includes(item.expectMessage)
      checks.check(`ATK-03-${item.key}`, `时间边界「${item.key}」按预期处理`, okStatus && okCode && okMessage,
        `status=${response.status} code=${response.responseBody?.code} message=${response.responseBody?.message || '-'}`)
      boundaryResults.push({ case: item, response })
    }

    const user1MineAfterBoundary = await getJson('/api/bookings/mine', user1.token)
    const adminBookingsAfterBoundary = await getJson('/api/admin/bookings', admin.token)
    const legalSubject = 'W11-时间边界-首尾相接'
    const illegalSubjects = boundaryCases
      .filter((item) => item.key !== '首尾相接')
      .map((item) => item.subject)

    const legalPresent = user1MineAfterBoundary.some((item) => item.subject === legalSubject)
    const illegalPresent = user1MineAfterBoundary
      .filter((item) => illegalSubjects.includes(item.subject))
      .map((item) => item.subject)
    const onlyLegalAdded =
      user1MineAfterBoundary.length === user1MineBeforeBoundary.length + 1 &&
      adminBookingsAfterBoundary.length === adminBookingsBeforeBoundary.length + 1 &&
      legalPresent &&
      illegalPresent.length === 0

    checks.check('ATK-03-db', '库中只新增合法的一条，非法时段零落库', onlyLegalAdded,
      `我的预约 ${user1MineBeforeBoundary.length}→${user1MineAfterBoundary.length}；非法命中 ${illegalPresent.length} 条`)

    await writeText(path.join('docs', 'week11', '取证', '03_时间边界', '01_请求.txt'),
      boundaryResults.map((item) => renderRequest(item.response)).join('\n'))
    await writeText(path.join('docs', 'week11', '取证', '03_时间边界', '02_响应.txt'),
      boundaryResults.map((item) => renderResponse(item.response)).join('\n'))
    await writeText(path.join('docs', 'week11', '取证', '03_时间边界', '03_库内数据.txt'), [
      `取证时间：${nowLocalString()}`,
      `构造基线：${adjacencySeed.roomName}（roomId=${adjacencySeed.roomId}）明天已有 ${adjacencySeed.startTime} — ${adjacencySeed.endTime} 的预约`,
      `非法时段会议室：${freeRoom.name}（roomId=${freeRoom.id}），明天与后天均无已占用时段`,
      `首尾相接提交：${adjacencyStart} — ${adjacencyEnd}（同一天、不重叠）`,
      '',
      '--- 我的预约条数 ---',
      `提交前：${user1MineBeforeBoundary.length} 条`,
      `提交后：${user1MineAfterBoundary.length} 条`,
      '',
      '--- 管理端预约总数 ---',
      `提交前：${adminBookingsBeforeBoundary.length} 条`,
      `提交后：${adminBookingsAfterBoundary.length} 条`,
      '',
      '--- 四条提交的落库结果 ---',
      ...boundaryResults.map((item) => {
        const found = user1MineAfterBoundary.filter((booking) => booking.subject === item.case.subject)
        return `${item.case.key}：响应 ${item.response.status} / code ${item.response.responseBody?.code}，库内 ${found.length} 条`
      }),
      '',
      `结论：${onlyLegalAdded
        ? '跨天、超 8 小时、结束早于开始三种非法时段均未落库；首尾相接按半开区间放行并落库 1 条。'
        : '落库结果与预期不一致，需立即排查。'}`,
      '',
      '说明：本目录没有 MySQL，库内数据用“我的预约 + 管理端全部预约”接口回读。',
      '正式后端复跑时改用：SELECT id, subject, start_time, end_time FROM reservation WHERE subject LIKE \'W11-时间边界-%\';',
      ''
    ].join('\n'))
    await writeJson(path.join('docs', 'week11', '取证', '03_时间边界', 'result.json'), {
      executedAt: nowLocalString(),
      adjacencySeed: bookingBrief(adjacencySeed),
      cases: boundaryResults.map((item) => ({
        key: item.case.key,
        request: {
          roomId: item.case.roomId,
          subject: item.case.subject,
          startTime: item.case.startTime,
          endTime: item.case.endTime
        },
        status: item.response.status,
        body: item.response.responseBody
      })),
      before: {
        mine: user1MineBeforeBoundary.length,
        admin: adminBookingsBeforeBoundary.length
      },
      after: {
        mine: user1MineAfterBoundary.length,
        admin: adminBookingsAfterBoundary.length
      },
      legalPresent,
      illegalPresent,
      onlyLegalAdded
    })

    // ---------- TC-12：我的预约只返回本人 ----------
    const user1Mine = await getJson('/api/bookings/mine', user1.token)
    const user2Mine = await getJson('/api/bookings/mine', user2.token)
    const adminBookings = await getJson('/api/admin/bookings', admin.token)
    const ownerById = new Map(adminBookings.map((item) => [item.id, item.username]))

    const user1OnlyOwn = user1Mine.every((item) => ownerById.get(item.id) === 'user001')
    const user2OnlyOwn = user2Mine.every((item) => ownerById.get(item.id) === 'user002')
    const noOverlap = user1Mine.every((item) => !user2Mine.some((other) => other.id === item.id))
    const statuses = ['PENDING', 'ONGOING', 'FINISHED', 'CANCELLED']
    const statusValid = [...user1Mine, ...user2Mine].every((item) => statuses.includes(item.status))

    checks.check('TC-12', '普通用户只看到本人预约', user1OnlyOwn && user2OnlyOwn && noOverlap,
      `user001 ${user1Mine.length} 条；user002 ${user2Mine.length} 条`)
    checks.check('TC-12-status', '我的预约状态均为四态之一', statusValid,
      [...new Set([...user1Mine, ...user2Mine].map((item) => item.status))].join(', '))

    const summary = checks.summary()
    const result = {
      executedAt: startedAt,
      finishedAt: nowLocalString(),
      baseUrl,
      environment: serviceAlreadyRunning ? 'existing-local-service' : 'mock-start-by-script',
      accounts: ['user001', 'user002', 'admin001'],
      boundary: { tomorrow, dayAfter },
      counts: {
        user1MineBefore: user1MineBefore.length,
        user2MineBefore: user2MineBefore.length,
        adminBookingsBefore: adminBookingsBefore.length,
        logsBefore: logsBefore.length,
        user1MineAfter: user1Mine.length,
        user2MineAfter: user2Mine.length,
        adminBookingsAfter: adminBookings.length
      },
      checks: checks.items,
      summary
    }
    await writeJson(path.join('docs', 'week11', '取证', 'api-result.json'), result)

    console.log('')
    console.log(`API 检查汇总：${summary.passed}/${summary.total} 通过，${summary.failed} 失败`)
    console.log(`结果文件：${path.join(EVIDENCE_DIR, 'api-result.json')}`)
    if (summary.failed > 0) process.exitCode = 1
  } finally {
    if (mock) await mock.stop()
  }
}

main().catch((error) => {
  console.error(`[FATAL] ${error.message}`)
  process.exitCode = 1
})
