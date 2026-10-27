import { request } from '@/api/http'
import type { BookingRequest, ReservationCreateVO } from '@/types/api'

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
