import { api } from '@/lib/api'
import type { AuthenticationResult, HealthStatus, User } from '@/types/api'

export interface RegisterPayload {
  email: string
  password: string
  displayName: string
  inviteCode?: string
}

export interface LoginPayload {
  email: string
  password: string
}

export interface RegistrationOptions {
  inviteRequired: boolean
}

export const authApi = {
  registration: () => api.get<RegistrationOptions>('/auth/registration'),
  register: (payload: RegisterPayload) => api.post<AuthenticationResult>('/auth/register', payload),
  login: (payload: LoginPayload) => api.post<AuthenticationResult>('/auth/login', payload),
  me: () => api.get<User>('/auth/me'),
}

export const systemApi = {
  health: () => api.get<HealthStatus>('/health'),
}
