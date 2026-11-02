import { request } from '@/api/http'
import type { BookingItemVO, BookingRequest, ReservationCreateVO } from '@/types/api'

/**
 * 提交预约 —— 对应第 8 周接口清单第 5 条 / FR-07、FR-09、FR-10。
 *
 * <p>★ 冲突时的返回是 409，抛出的 {@link ApiBusinessError} 里带
 * {@code data.occupiedSlots}（被占用的时段），页面据此展示
 * "该会议室在此时间段已被占用"＋具体是哪几段（FR-10）。</p>
 *
 * @param payload 预约信息
 * @returns 预约ID、编号与状态
 */
export function createBooking(payload: BookingRequest): Promise<ReservationCreateVO> {
  return request<ReservationCreateVO>({
    url: '/bookings',
    method: 'post',
    data: payload
  })
}

/* ==========================================================================================
 * 第 10 周新增（接口 #6／#7／#8，虞业宸后端实现）。三条都是"本人"口径：
 * 后端从令牌解出 userId，前端不传、也无法传——传了也不认，避免"改个头就以他人身份操作"。
 * ========================================================================================== */

/**
 * 我的预约（接口 #6，FR-11）。
 *
 * <p>★ 返回的 {@code status} 是后端<b>读取时推导</b>的四态，前端直接显示，
 * <b>不按时间自己算</b>——否则"过去但库里还是 PENDING"的记录两边会显示不一致（NF-08 单一口径）。</p>
 *
 * <p>副作用（预期内）：后端读取时会顺手把全表中已结束的 PENDING 校正为 FINISHED，
 * 这是 NF-08"不引入定时器"的实现方式。</p>
 */
export function listMyBookings(): Promise<BookingItemVO[]> {
  return request<BookingItemVO[]>({
    url: '/bookings/mine',
    method: 'get'
  })
}

/**
 * 修改我的预约（接口 #7，FR-12）。
 *
 * <p>后端会拿新时段<b>重跑一次冲突检测并排除自身</b>——不排自身的话，
 * "只改用途不改时间"会被自己那条记录判冲突。以下情况返回 400／403 并给中文原因：
 * 非本人（403）、已开始或已结束、已取消、距开始不足 30 分钟。</p>
 */
export function modifyBooking(id: number, payload: BookingRequest): Promise<ReservationCreateVO> {
  return request<ReservationCreateVO>({
    url: `/bookings/${id}`,
    method: 'put',
    data: payload
  })
}

/**
 * 取消我的预约（接口 #8，FR-13）。
 *
 * <p>取消后时段<b>立即释放</b>（NF-08）：再回会议室详情页看，那一格应回到"可订"状态。
 * 原因选填——用户自己取消不强制填原因，管理员强制取消才必填（FR-16）。</p>
 */
export function cancelBooking(id: number, reason?: string): Promise<ReservationCreateVO> {
  return request<ReservationCreateVO>({
    url: `/bookings/${id}`,
    method: 'delete',
    data: reason ? { reason } : {}
  })
}
