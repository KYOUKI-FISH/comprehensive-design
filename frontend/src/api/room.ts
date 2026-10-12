import { request } from '@/api/http'
import type { MeetingRoom } from '@/types/api'

/** 会议室查询条件（对应 DFD 中 P2 的输入数据流"查询条件"） */
export interface RoomQuery {
  /** 名称/位置模糊关键字 */
  keyword?: string
  /** 最小容纳人数 */
  minCapacity?: number
}

/**
 * 查询启用中的会议室列表 —— 对应 DFD 加工 P2 查询会议室 / FR-05、FR-06。
 *
 * <p>骨架阶段不返回"当日可用时段"：那要读 D3 预约记录并结合半开区间规则计算，
 * 属第 9～10 周业务实现范围。
 */
export function listRooms(params: RoomQuery = {}): Promise<MeetingRoom[]> {
  return request<MeetingRoom[]>({
    url: '/rooms',
    method: 'get',
    params
  })
}
