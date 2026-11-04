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

/* ==========================================================================================
 * 以下为第 10 周新增类型（我的预约、管理端 4 组接口）。
 * 字段与 backend 的 dto 包逐一对应，不做"顺手多给几个字段"——前后端各写各的字段名，
 * 联调时会花大量时间确认"这个字段到底有没有"。
 * ========================================================================================== */

/** 预约状态四态（NF-08）；与 backend 的 ReservationStatusPolicy 取值一致 */
export type ReservationStatus = 'PENDING' | 'ONGOING' | 'FINISHED' | 'CANCELLED'

/**
 * 分页结果包装（与 backend 的 dto.PageResult 对应）。
 *
 * <p>管理端两个列表（#9 全部预约、#13 操作日志）都返回它。只有拿到 {@code total}
 * 才能算总页数，翻页控件才做得出来。</p>
 */
export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

/**
 * "我的预约"列表项（与 backend 的 dto.BookingItemVO 对应，接口 #6）。
 *
 * <p>★ {@code status} 是<b>后端读取时推导后的四态</b>，不是库里的原始存储值。
 * 前端<b>不许</b>按时间自己算状态——那样"过去但库里还是 PENDING"的记录会两边显示不一致，
 * 也绕开了 NF-08 的单一口径。</p>
 */
export interface BookingItemVO {
  id: number
  reservationNo: string
  roomId: number
  roomName: string
  location: string
  /** 预约日期 yyyy-MM-dd */
  date: string
  /** yyyy-MM-dd HH:mm:ss（或精确到分钟） */
  startTime: string
  endTime: string
  topic: string
  attendeeCount: number
  contact: string
  purpose: string | null
  status: ReservationStatus
  /** 取消原因：用户自己取消可空；被管理员强制取消时有值 */
  cancelReason: string | null
}

/**
 * 管理端预约视图（与 backend 的 dto.ReservationDetailDTO 对应，接口 #9／#10）。
 *
 * <p>比 {@link BookingItemVO} 多三样：{@code userName}（谁订的）、
 * {@code operatorName}（最后强制取消它的管理员）、{@code createdAt}／{@code updatedAt}。</p>
 */
export interface ReservationDetailVO {
  id: number
  reservationNo: string
  userId: number
  userName: string | null
  roomId: number
  roomName: string
  /** 预约日期 yyyy-MM-dd */
  reserveDate: string
  startTime: string
  endTime: string
  topic: string
  attendeeCount: number
  contact: string
  purpose: string | null
  status: ReservationStatus
  cancelReason: string | null
  operatorName: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** 用户视图（与 backend 的 dto.UserVO 对应，接口 #12）。★ 后端刻意不含 password */
export interface UserVO {
  id: number
  username: string
  realName: string | null
  role: 'USER' | 'ADMIN'
  phone: string | null
  /** ACTIVE=正常，DISABLED=已停用（FR-14 的下线方式；本系统不删用户） */
  status: 'ACTIVE' | 'DISABLED'
  createdAt: string | null
}

/** 操作日志视图（与 backend 的 dto.OperationLogVO 对应，接口 #13，NF-14） */
export interface OperationLogVO {
  id: number
  /** 操作人账号快照（操作人事后改名不影响历史记录） */
  operatorUsername: string | null
  operatorName: string | null
  operatorRole: string | null
  operationType: string
  targetType: string
  targetId: number | null
  /** 变更前数据（JSON 串原样返回） */
  beforeData: string | null
  afterData: string | null
  remark: string | null
  /** 后端拼好的人类可读摘要，前端直接显示，不必自己解析 JSON */
  detail: string | null
  /** 建表脚本无 ip 列，后端固定返回 null */
  ip: string | null
  createdAt: string
}

/** 全部预约查询条件（接口 #9；参数全部可选，一条不传即返回全部） */
export interface AdminBookingQuery {
  userId?: number
  roomId?: number
  status?: ReservationStatus
  /** yyyy-MM-dd */
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}

/** 操作日志查询条件（接口 #13；参数全部可选） */
export interface AdminLogQuery {
  operatorId?: number
  operationType?: string
  targetType?: string
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}

/** 新增／修改会议室请求体（接口 #11） */
export interface RoomUpsertRequest {
  name: string
  location: string
  capacity: number
  equipment?: string
  remark?: string
  status?: 'ENABLED' | 'DISABLED'
}

/** 新建用户请求体（接口 #12） */
export interface UserCreateRequest {
  username: string
  password: string
  realName?: string
  phone?: string
  role?: 'USER' | 'ADMIN'
}

/** 修改用户请求体（接口 #12 PUT；至少一项） */
export interface UserUpdateRequest {
  realName?: string
  phone?: string
  role?: 'USER' | 'ADMIN'
  status?: 'ACTIVE' | 'DISABLED'
  /** 只在校验通过后由后端 BCrypt 落库，明文不落库也不返回 */
  password?: string
}
