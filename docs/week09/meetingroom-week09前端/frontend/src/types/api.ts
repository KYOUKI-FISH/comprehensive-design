export type UserRole = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED'
export type RoomStatus = 'AVAILABLE' | 'DISABLED'
export type ReservationStatus = 'PENDING' | 'ONGOING' | 'FINISHED' | 'CANCELLED'

/**
 * 后端统一返回体。HTTP 状态码表达成功/失败，data 承载业务数据。
 * 失败时 data 仍可能带业务信息（例如 409 冲突的 occupiedSlots）。
 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export interface UserVO {
  id: number
  username: string
  name: string
  department?: string
  role: UserRole
  status: UserStatus
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  /** 令牌有效期（秒）。后端未返回时前端按 30 分钟处理。 */
  expiresIn?: number
  user: UserVO
}

export interface RoomVO {
  id: number
  name: string
  location: string
  capacity: number
  /** 后端可能返回数组或逗号分隔字符串，展示前统一格式化。 */
  equipment?: string[] | string | null
  status: RoomStatus
}

export interface RoomQuery {
  name?: string
  location?: string
  minCapacity?: number
}

export interface TimeSlot {
  /** 本地时间，格式 YYYY-MM-DDTHH:mm:ss */
  startTime: string
  endTime: string
}

export type OccupiedSlot = TimeSlot

export interface OccupancyVO {
  roomId: number
  roomName: string
  date: string
  occupiedSlots: OccupiedSlot[]
  freeSlots: TimeSlot[]
}

export interface CreateBookingRequest {
  roomId: number
  subject: string
  attendees: number
  contact: string
  startTime: string
  endTime: string
}

export interface BookingVO {
  id: number
  reservationNo: string
  roomId: number
  roomName: string
  subject: string
  attendees: number
  contact: string
  startTime: string
  endTime: string
  status: ReservationStatus
  createdAt: string
}

/** 409 冲突响应中的 data 形状（FR-10） */
export interface ConflictData {
  occupiedSlots: OccupiedSlot[]
}
