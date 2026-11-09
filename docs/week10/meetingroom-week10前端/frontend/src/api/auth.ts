import { http } from './http'
import type { LoginRequest, LoginResponse } from '@/types/api'

export function login(payload: LoginRequest): Promise<LoginResponse> {
  return http.post<LoginResponse>('/auth/login', payload, false)
}

export function logout(): Promise<null> {
  return http.post<null>('/auth/logout')
}
