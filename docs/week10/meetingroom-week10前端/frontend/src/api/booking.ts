import { ApiError, http } from './http'
import type {
  BookingVO,
  CreateBookingRequest,
  OccupiedSlot,
  UpdateBookingRequest
} from '@/types/api'

export function createBooking(payload: CreateBookingRequest): Promise<BookingVO> {
  return http.post<BookingVO>('/bookings', payload)
}

/** FR-11：当前登录用户的预约，状态由后端按四态返回 */
export function listMyBookings(): Promise<BookingVO[]> {
  return http.get<BookingVO[]>('/bookings/mine')
}

/** FR-12：修改未开始的预约，后端重新做冲突检测 */
export function updateBooking(
  bookingId: number,
  payload: UpdateBookingRequest
): Promise<BookingVO> {
  return http.put<BookingVO>(`/bookings/${bookingId}`, payload)
}

/** FR-13：取消未开始的预约，后端立即释放时段 */
export function cancelBooking(bookingId: number): Promise<BookingVO> {
  return http.delete<BookingVO>(`/bookings/${bookingId}`)
}

/**
 * 从 409 冲突异常中取出被占用时段（FR-10）。
 * 兼容 data 缺失或形状不符的情况，页面只展示能确认的字段。
 */
export function extractOccupiedSlots(error: unknown): OccupiedSlot[] {
  if (!(error instanceof ApiError)) return []

  const data = error.data as { occupiedSlots?: unknown } | null | undefined
  if (!data || !Array.isArray(data.occupiedSlots)) return []

  return data.occupiedSlots.filter((slot): slot is OccupiedSlot => {
    if (!slot || typeof slot !== 'object') return false
    const candidate = slot as Partial<OccupiedSlot>
    return typeof candidate.startTime === 'string' && typeof candidate.endTime === 'string'
  })
}
