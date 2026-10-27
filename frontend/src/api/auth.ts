import { request } from '@/api/http'
import type { LoginRequest, LoginResponse } from '@/types/api'

/**
 * 登录接口 —— 对应 DFD 加工 P1 验证登录 / FR-01 用户登录。
 *
 * @param payload 账号与密码
 * @returns 登录结果、角色与<strong>会话令牌</strong>；账号密码错误时抛出 Error("账号或密码错误")
 */
export function login(payload: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>({
    url: '/auth/login',
    method: 'post',
    data: payload
  })
}

/**
 * 登出接口（第 9 周；第 8 周接口清单第 2 条，NF-04）。
 *
 * <p>★ 是<strong>服务端作废</strong>令牌，不只是前端删一下：
 * 只在前端清 token 的话，旧令牌在有效期内仍能调接口（会话劫持场景）。</p>
 *
 * <p>重复调用按幂等处理，仍返回成功。</p>
 */
export function logout(): Promise<void> {
  return request<void>({
    url: '/auth/logout',
    method: 'post'
  })
}
