import type {
  OperationTargetType,
  OperationType,
  ReservationStatus,
  RoomStatus,
  UserRole,
  UserStatus
} from '@/types/api'

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

const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: '正常',
  DISABLED: '已停用'
}

const OPERATION_TYPE_LABELS: Record<OperationType, string> = {
  CREATE_USER: '新增用户',
  UPDATE_USER: '修改用户',
  ROLE_CHANGE: '角色变更',
  CREATE_ROOM: '新增会议室',
  UPDATE_ROOM: '修改会议室',
  DELETE_ROOM: '删除会议室',
  FORCE_CANCEL: '强制取消预约'
}

const TARGET_TYPE_LABELS: Record<OperationTargetType, string> = {
  USER: '用户',
  ROOM: '会议室',
  BOOKING: '预约'
}

/** Element Plus el-tag 支持的类型，状态展示统一走组件库。 */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export function roomStatusLabel(status: RoomStatus): string {
  return ROOM_STATUS_LABELS[status] ?? status
}

export function roomStatusTagType(status: RoomStatus): TagType {
  return status === 'AVAILABLE' ? 'success' : 'info'
}

export function reservationStatusLabel(status: ReservationStatus): string {
  return RESERVATION_STATUS_LABELS[status] ?? status
}

export function reservationStatusTagType(status: ReservationStatus): TagType {
  switch (status) {
    case 'PENDING':
      return 'primary'
    case 'ONGOING':
      return 'warning'
    case 'FINISHED':
      return 'info'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'info'
  }
}

export function roleLabel(role: UserRole): string {
  return ROLE_LABELS[role] ?? role
}

export function userStatusLabel(status: UserStatus): string {
  return USER_STATUS_LABELS[status] ?? status
}

export function userStatusTagType(status: UserStatus): TagType {
  return status === 'ACTIVE' ? 'success' : 'info'
}

export function operationTypeLabel(type: OperationType): string {
  return OPERATION_TYPE_LABELS[type] ?? type
}

export function targetTypeLabel(type: OperationTargetType): string {
  return TARGET_TYPE_LABELS[type] ?? type
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
