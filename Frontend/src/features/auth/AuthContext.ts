import { createContext } from 'react'

import type { LoginPayload, RegisterPayload } from '@/features/auth/authApi'
import type { User } from '@/types/api'

export interface AuthContextValue {
  user: User | null
  /** True while the stored session is being validated against the backend on first load. */
  isRestoring: boolean
  isAuthenticated: boolean
  login: (payload: LoginPayload) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
