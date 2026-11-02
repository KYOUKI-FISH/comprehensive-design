import { request } from '@/api/http'
import type {
  AdminBookingQuery,
  AdminLogQuery,
  OperationLogVO,
  PageResult,
  ReservationDetailVO,
  RoomUpsertRequest,
  RoomVO,
  UserCreateRequest,
  UserUpdateRequest,
  UserVO
} from '@/types/api'

/**
 * 管理端接口封装（第 10 周，接口 #9～#13）。
 *
 * <p>★ 所有路径都在 {@code /api/admin/**} 下。<b>这不是命名习惯，是安全边界</b>：
 * 后端 {@code AuthInterceptor} 按这个前缀判定"仅管理员"，接口一旦挪到前缀外，
 * 普通用户就能直接调到（NF-03 的边界当场漏）。新增管理端接口时务必保持该前缀。</p>
 *
 * <p>前端隐藏菜单只是体验层，真正的门在后端——所以本文件里的每个方法，
 * 被普通用户令牌调用时都应得到 {@code code=403}，页面要能把它显示成中文提示。</p>
 */

/* ------------------------------------------------------------------ 接口 #9 全部预约 */

/**
 * 全部预约（接口 #9，FR-15）。
 *
 * <p>筛选参数全部可选，一条不传即返回全部。分页参数不传走后端默认值（第 1 页 20 条）。</p>
 */
export function listAllBookings(query: AdminBookingQuery = {}): Promise<PageResult<ReservationDetailVO>> {
  return request<PageResult<ReservationDetailVO>>({
    url: '/admin/bookings',
    method: 'get',
    params: query
  })
}

/**
 * 强制取消异常预约（接口 #10，FR-16）。
 *
 * <p>★ {@code reason} 是<b>必填</b>且不少于 5 个字符——FR-16 原文"未填写原因时，
 * 系统拒绝处理并提示填写原因"。因此前端也不能把它做成选填：页面先校验，
 * 后端再校验一遍（前端校验只是省一次往返，不是防线）。</p>
 */
export function forceCancelBooking(id: number, reason: string): Promise<ReservationDetailVO> {
  return request<ReservationDetailVO>({
    url: `/admin/bookings/${id}/cancel`,
    method: 'put',
    data: { reason }
  })
}

/* ---------------------------------------------------------------- 接口 #11 会议室管理 */

/**
 * 会议室列表（接口 #11；与前台 #3 同口径，但<b>不过滤停用</b>——管理员要能看到停用的那间）。
 */
export function listRoomsForAdmin(params: {
  location?: string
  minCapacity?: number
  status?: 'ENABLED' | 'DISABLED'
} = {}): Promise<RoomVO[]> {
  return request<RoomVO[]>({
    url: '/admin/rooms',
    method: 'get',
    params
  })
}

/** 新增会议室（接口 #11，FR-03） */
export function createRoom(payload: RoomUpsertRequest): Promise<RoomVO> {
  return request<RoomVO>({
    url: '/admin/rooms',
    method: 'post',
    data: payload
  })
}

/**
 * 修改会议室（接口 #11，FR-03／FR-04）。
 *
 * <p>★ <b>停用也走这里</b>（传 {@code status: 'DISABLED'}）：需求清单里启用／停用
 * 不是独立接口，而是会议室的一个字段。单独再开一个 {@code /status} 接口会让
 * 接口总数从 14 条变多，第 11 周核对"接口总数 14 条"时会卡住。</p>
 */
export function updateRoom(id: number, payload: Partial<RoomUpsertRequest>): Promise<RoomVO> {
  return request<RoomVO>({
    url: `/admin/rooms/${id}`,
    method: 'put',
    data: payload
  })
}

/**
 * 删除会议室（接口 #11，FR-03）。
 *
 * <p>★ 有预约时后端返回 {@code code=409}「该会议室存在有效预约，请先处理预约」——
 * 这是数据库 {@code ON DELETE RESTRICT} 拦下来的，不是业务层假装拦的。
 * 会议室管理页必须能演示"删不掉"这个分支：它证明的是"历史预约不会因为删会议室而丢失归属"。</p>
 */
export function deleteRoom(id: number): Promise<void> {
  return request<void>({
    url: `/admin/rooms/${id}`,
    method: 'delete'
  })
}

/* ------------------------------------------------------------------ 接口 #12 用户管理 */

/** 用户列表（接口 #12，FR-14）。★ 出参不含 password（后端从类型层面保证） */
export function listUsers(params: { status?: 'ACTIVE' | 'DISABLED'; role?: 'USER' | 'ADMIN' } = {}): Promise<UserVO[]> {
  return request<UserVO[]>({
    url: '/admin/users',
    method: 'get',
    params
  })
}

/** 新建用户（接口 #12，FR-14） */
export function createUser(payload: UserCreateRequest): Promise<UserVO> {
  return request<UserVO>({
    url: '/admin/users',
    method: 'post',
    data: payload
  })
}

/**
 * 修改用户（接口 #12，FR-14）：启停／改名／改电话／改角色／重置口令，至少一项。
 *
 * <p>★ <b>没有删除用户</b>：FR-14 只要求"新建与停用"。而且 {@code reservation.user_id}
 * 与 {@code operation_log.operator_id} 都是 {@code ON DELETE RESTRICT}，
 * 删用户会破坏历史预约与审计日志的归属——"停用"才是正确的下线方式。</p>
 */
export function updateUser(id: number, payload: UserUpdateRequest): Promise<UserVO> {
  return request<UserVO>({
    url: `/admin/users/${id}`,
    method: 'put',
    data: payload
  })
}

/* ------------------------------------------------------------------ 接口 #13 操作日志 */

/**
 * 操作日志查询（接口 #13，NF-14）。
 *
 * <p>★ <b>只有查询，没有删除</b>：NF-14 要求日志"不能被业务界面删除"，是设计上就不该有。
 * 加一个删除接口，审计链就失去可追溯的意义。</p>
 */
export function listOperationLogs(query: AdminLogQuery = {}): Promise<PageResult<OperationLogVO>> {
  return request<PageResult<OperationLogVO>>({
    url: '/admin/logs',
    method: 'get',
    params: query
  })
}
