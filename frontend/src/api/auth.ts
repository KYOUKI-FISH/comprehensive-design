import { request } from '@/api/http'
import type { LoginRequest, LoginResponse } from '@/types/api'

/**
 * 登录接口 —— 对应 DFD 加工 P1 验证登录 / FR-01 用户登录。
 *
 * @param payload 账号与密码
 * @returns 登录结果与角色；账号密码错误时抛出 Error("账号或密码错误")
 */
export function login(payload: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>({
    url: '/auth/login',
    method: 'post',
    data: payload
  })
}
