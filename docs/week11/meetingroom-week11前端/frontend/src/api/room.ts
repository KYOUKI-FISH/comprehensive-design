import { http } from './http'
import type { OccupancyVO, RoomQuery, RoomVO } from '@/types/api'

export function listRooms(query: RoomQuery = {}): Promise<RoomVO[]> {
  const params = new URLSearchParams()

  if (query.name?.trim()) params.set('name', query.name.trim())
  if (query.location?.trim()) params.set('location', query.location.trim())
  if (typeof query.minCapacity === 'number' && query.minCapacity > 0) {
    params.set('minCapacity', String(query.minCapacity))
  }

  const queryString = params.toString()
  return http.get<RoomVO[]>(`/rooms${queryString ? `?${queryString}` : ''}`)
}

/**
 * D1 决策：不新增 GET /api/rooms/{id}。
 * 详情页的会议室基本信息复用 listRooms() 的 RoomVO，占用时段由本接口提供。
 */
export function getOccupancy(roomId: number, date: string): Promise<OccupancyVO> {
  return http.get<OccupancyVO>(
    `/rooms/${roomId}/occupancy?date=${encodeURIComponent(date)}`
  )
}
