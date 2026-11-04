import { http } from './http'
import type {
  AdminBookingQuery,
  AdminBookingVO,
  AdminRoomRequest,
  AdminUserVO,
  CreateUserRequest,
  OperationLogVO,
  RoomVO,
  UpdateUserRequest
} from '@/types/api'

/** FR-15：全部预约，支持按会议室或日期筛选（FR-19 简化版） */
export function listAdminBookings(query: AdminBookingQuery = {}): Promise<AdminBookingVO[]> {
  const params = new URLSearchParams()
  if (typeof query.roomId === 'number' && query.roomId > 0) {
    params.set('roomId', String(query.roomId))
  }
  if (query.date) params.set('date', query.date)

  const queryString = params.toString()
  return http.get<AdminBookingVO[]>(
    `/admin/bookings${queryString ? `?${queryString}` : ''}`
  )
}

/** FR-16：强制取消异常预约，原因由页面必填校验、后端再次校验 */
export function forceCancelBooking(bookingId: number, reason: string): Promise<AdminBookingVO> {
  return http.put<AdminBookingVO>(`/admin/bookings/${bookingId}/cancel`, { reason })
}

export function listAdminRooms(): Promise<RoomVO[]> {
  return http.get<RoomVO[]>('/admin/rooms')
}

export function createRoom(payload: AdminRoomRequest): Promise<RoomVO> {
  return http.post<RoomVO>('/admin/rooms', payload)
}

export function updateRoom(roomId: number, payload: AdminRoomRequest): Promise<RoomVO> {
  return http.put<RoomVO>(`/admin/rooms/${roomId}`, payload)
}

/** FR-03：存在有效预约时后端返回 409，页面展示中文原因 */
export function deleteRoom(roomId: number): Promise<null> {
  return http.delete<null>(`/admin/rooms/${roomId}`)
}

export function listAdminUsers(): Promise<AdminUserVO[]> {
  return http.get<AdminUserVO[]>('/admin/users')
}

export function createUser(payload: CreateUserRequest): Promise<AdminUserVO> {
  return http.post<AdminUserVO>('/admin/users', payload)
}

export function updateUser(
  userId: number,
  payload: UpdateUserRequest
): Promise<AdminUserVO> {
  return http.put<AdminUserVO>(`/admin/users/${userId}`, payload)
}

/** NF-14：操作日志只读，无删除/修改接口 */
export function listOperationLogs(): Promise<OperationLogVO[]> {
  return http.get<OperationLogVO[]>('/admin/logs')
}
