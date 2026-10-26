/**
 * 前后端接口的数据结构定义。
 *
 * 第 6 周骨架阶段只有登录与会议室查询两个接口；第 9 周补上会话令牌、
 * 会议室详情、占用时段与提交预约相关类型，字段与后端 com.meetingroom 一一对应。
 */

/** 后端统一返回体（与 backend 的 com.meetingroom.common.ApiResponse 对应） */
export interface ApiResponse<T> {
  /** 200=成功；400 入参不合法；401 账号或密码错误／令牌失效；403 无权限／账号被停用；404 资源不存在；409 预约冲突；500 服务器内部错误 */
  code: number
  message: string
  data: T
}

/** 登录请求（P1 验证登录） */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录返回（与 backend 的 com.meetingroom.dto.LoginResponse 对应） */
export interface LoginResponse {
  userId: number
  username: string
  realName: string | null
  /** USER=普通用户，ADMIN=管理员（FR-02 角色区分） */
  role: 'USER' | 'ADMIN'
  /** 会话令牌（第 9 周；NF-04 有效期 30 分钟）。后续请求通过 Authorization 头带回 */
  token: string
}

/** 会议室列表项（与 backend 的 com.meetingroom.dto.RoomVO 对应）—— 对应 DFD 数据存储 D2 会议室信息 */
export interface RoomVO {
  id: number
  name: string
  /** 位置（如"逸夫楼A座1层"） */
  location: string
  /** 可容纳人数 */
  capacity: number
  /** 设备描述，仅作展示 */
  equipment: string
  /** ENABLED=启用，DISABLED=停用（FR-06 只显示启用中的会议室） */
  status: 'ENABLED' | 'DISABLED'
  /** 传了 date 参数时该会议室当天的已占用时段，形如 "09:00-10:00"；未传时为空数组 */
  occupiedSlots: string[]
}

/**
 * 占用时段元素（第 9 周；与 backend 的 com.meetingroom.dto.OccupiedSlotVO 对应）。
 *
 * <p>给的是起止时间点而非区间文本：前端要能据此标出"哪几段被占了"。</p>
 */
export interface OccupiedSlotVO {
  /** 占用开始时间（含本时刻，半开区间左闭） */
  startTime: string
  /** 占用结束时间（不含本时刻，半开区间右开） */
  endTime: string
}

/** 会议室占用时段响应（第 9 周；与 backend 的 com.meetingroom.dto.OccupancyVO 对应） */
export interface OccupancyVO {
  roomId: number
  roomName: string
  date: string
  /** 已占用时段；只含未取消的预约（已取消的时段已释放） */
  occupiedSlots: OccupiedSlotVO[]
}

/** 提交预约请求（与 backend 的 com.meetingroom.dto.BookingRequest 对应） */
export interface BookingRequest {
  roomId: number
  /** 预约日期 yyyy-MM-dd */
  date: string
  /** 开始时间 yyyy-MM-dd HH:mm */
  startTime: string
  /** 结束时间 yyyy-MM-dd HH:mm */
  endTime: string
  /** 会议主题，必填（FR-07） */
  topic: string
  /** 参会人数，必填且大于 0（FR-07） */
  attendeeCount: number
  /** 联系人，必填（FR-07） */
  contact: string
  /** 用途补充说明，选填 */
  purpose?: string
}

/**
 * 提交预约的返回（与 backend 的 com.meetingroom.dto.ReservationCreateVO 对应）。
 *
 * <p>★ 同一个类型也用于 409 冲突响应：此时 {@code reservationNo} 为 null，
 * {@code occupiedSlots} 有值——前端据此展示"被占用的是哪几个时段"（FR-10）。</p>
 */
export interface ReservationCreateVO {
  id: number | null
  reservationNo: string | null
  /** 新建预约固定为 PENDING（待开始） */
  status: string | null
  /** 仅 409 冲突时有值 */
  occupiedSlots?: OccupiedSlotVO[]
}
