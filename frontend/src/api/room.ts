import { request } from '@/api/http'
import type { OccupancyVO, RoomVO } from '@/types/api'

/** 会议室查询条件（对应 DFD 中 P2 的输入数据流"查询条件"） */
export interface RoomQuery {
  /** 位置/名称模糊关键字（后端参数名为 location） */
  location?: string
  /** 最小容纳人数 */
  minCapacity?: number
  /** 启用状态。查询页固定传 ENABLED，停用的会议室不出现在前台（FR-06、问题 P03） */
  status?: 'ENABLED' | 'DISABLED'
  /** 需要展示当日已占用时段的日期（yyyy-MM-dd）；不传则返回元素的 occupiedSlots 恒为空 */
  date?: string
}

/**
 * 查询会议室列表 —— 对应 DFD 加工 P2 查询会议室 / FR-05 按条件查询、FR-06 只显示启用中的会议室。
 *
 * <p>传 {@code date} 时后端会附带该会议室当天的**已占用时段**（形如 {@code "09:00-10:00"}）。
 * 本页只做展示，不据此推荐替代时段 —— 按需求评审口径，替代时段推荐不在本期范围内。</p>
 */
export function listRooms(params: RoomQuery = {}): Promise<RoomVO[]> {
  return request<RoomVO[]>({
    url: '/rooms',
    method: 'get',
    params
  })
}

/**
 * 查会议室占用时段 —— 对应第 8 周接口清单第 4 条 / FR-17、FR-10。
 *
 * <p>★ 用途有二，数据同源：
 * <ol>
 *   <li>详情页展示"所选日期已被占用的时段"（FR-17，本周先做占位时段视图）；</li>
 *   <li>提交预约冲突时，409 响应里的被占用时段（FR-10）——那是后端在事务里查的同一条数据。</li>
 * </ol>
 * 只含未取消的预约：已取消的预约时段已释放（NF-08）。</p>
 *
 * @param roomId 会议室ID
 * @param date   日期 yyyy-MM-dd（必填）
 */
export function getOccupancy(roomId: number, date: string): Promise<OccupancyVO> {
  return request<OccupancyVO>({
    url: `/rooms/${roomId}/occupancy`,
    method: 'get',
    params: { date }
  })
}
