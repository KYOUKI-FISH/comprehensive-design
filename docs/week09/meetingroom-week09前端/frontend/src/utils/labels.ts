import type { ReservationStatus, RoomStatus, UserRole } from '@/types/api'

const ROOM_STATUS_LABELS: Record<RoomStatus, string> = {
  AVAILABLE: '可预约',
  DISABLED: '已停用'
}

const RESERVATION_STATUS_LABELS: Record<ReservationStatus, string> = {
  PENDING: '待开始',
  ONGOING: '进行中',
  FINISHED: '已结束',
  CANCELLED: '已取消'
}

const ROLE_LABELS: Record<UserRole, string> = {
  USER: '普通用户',
  ADMIN: '管理员'
}

export function roomStatusLabel(status: RoomStatus): string {
  return ROOM_STATUS_LABELS[status] ?? status
}

export function roomStatusClass(status: RoomStatus): string {
  return status === 'AVAILABLE' ? 'badge badge--success' : 'badge badge--muted'
}

export function reservationStatusLabel(status: ReservationStatus): string {
  return RESERVATION_STATUS_LABELS[status] ?? status
}

export function roleLabel(role: UserRole): string {
  return ROLE_LABELS[role] ?? role
}

export function formatEquipment(equipment: string[] | string | null | undefined): string {
  if (Array.isArray(equipment)) {
    return equipment.length > 0 ? equipment.join('、') : '无'
  }
  if (typeof equipment === 'string') {
    const trimmed = equipment.trim()
    if (!trimmed) return '无'
    return trimmed
      .split(/[,，、]/)
      .map((item) => item.trim())
      .filter(Boolean)
      .join('、')
  }
  return '无'
}
