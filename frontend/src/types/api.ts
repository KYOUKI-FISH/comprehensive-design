/**
 * 前后端接口的数据结构定义。
 *
 * 骨架阶段只有登录与会议室查询两个接口，字段与后端 com.meetingroom 一一对应：
 *   ApiResponse / LoginRequest / LoginResponse / RoomVO
 * 第 7 周接口设计阶段会在这里补全错误码枚举与预约相关类型。
 */

/** 后端统一返回体（与 backend 的 com.meetingroom.common.ApiResponse 对应） */
export interface ApiResponse<T> {
  /** 200=成功；400 入参不合法；401 账号或密码错误；403 账号被停用；500 服务器内部错误 */
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
