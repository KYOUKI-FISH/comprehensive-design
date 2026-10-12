/**
 * 前后端接口的数据结构定义。
 *
 * 骨架阶段只有登录与会议室查询两个接口，字段与后端一一对应：
 *   ApiResult / LoginRequest / LoginResponse / MeetingRoom
 * 第 7 周接口设计阶段会在这里补全错误码枚举与预约相关类型。
 */

/** 后端统一返回体（与 backend 的 cn.edu.uestc.meeting.common.ApiResult 对应） */
export interface ApiResult<T> {
  /** 200=成功；400 入参不合法；401 账号或密码错误；500 服务器内部错误 */
  code: number
  message: string
  data: T
}

/** 登录请求（P1 验证登录） */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录返回：登录结果 + 角色（对应 DFD 中 P1 的输出数据流） */
export interface LoginResponse {
  userId: number
  username: string
  realName: string | null
  /** USER=普通用户，ADMIN=管理员（FR-02 角色区分） */
  role: 'USER' | 'ADMIN'
}

/** 会议室（对应 DFD 数据存储 D2 会议室信息） */
export interface MeetingRoom {
  id: number
  name: string
  /** 位置（楼栋/楼层） */
  location: string
  /** 容纳人数 */
  capacity: number
  /** 1=启用，0=停用（查询接口只返回 1 的，见 FR-06） */
  status: number
  remark: string | null
  createdAt: string
  updatedAt: string
}
