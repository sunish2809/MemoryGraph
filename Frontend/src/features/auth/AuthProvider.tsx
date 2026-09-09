import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { AuthContext, type AuthContextValue } from '@/features/auth/AuthContext'
import { authApi, type LoginPayload, type RegisterPayload } from '@/features/auth/authApi'
import { onSessionExpired } from '@/lib/api'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { onIdleTimeout, startIdleWatch } from '@/lib/idleSession'
import { clearSession, readSession, rememberLogoutReason, writeSession } from '@/lib/session'
import type { AuthenticationResult, User } from '@/types/api'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [isRestoring, setIsRestoring] = useState(() => readSession() !== null)

  const applySession = useCallback((result: AuthenticationResult) => {
    writeSession({ accessToken: result.accessToken, expiresAt: result.expiresAt })
    setUser(result.user)
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setUser(null)
    // Memories are private: drop anything already fetched so it cannot be read by the next account.
    queryClient.clear()
  }, [queryClient])

  // A stored token is only trusted once the backend confirms it, which also refreshes the profile.
  // Network blips during a heavy import must not wipe the session — only a real 401 does.
  useEffect(() => {
    if (readSession() === null) return

    let cancelled = false

    async function restore() {
      let attempt = 0
      while (!cancelled) {
        try {
          const profile = await authApi.me()
          if (!cancelled) setUser(profile)
          return
        } catch (err) {
          const rejected =
            err instanceof ApiRequestError &&
            err.status === 401 &&
            (err.code === 'UNAUTHENTICATED' || err.code === 'INVALID_CREDENTIALS')
          if (rejected || readSession() === null) {
            if (!cancelled) {
              clearSession()
              setUser(null)
            }
            return
          }
          attempt += 1
          const delay = Math.min(8_000, 500 * 2 ** Math.min(attempt, 5))
          if (attempt === 1 || attempt % 5 === 0) {
            console.warn('Could not restore session; retrying', err)
          }
          await new Promise((r) => setTimeout(r, delay))
        }
      }
    }

    void restore().finally(() => {
      if (!cancelled) setIsRestoring(false)
    })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => onSessionExpired(logout), [logout])

  useEffect(() => {
    if (!user) return
    const stopWatch = startIdleWatch()
    const stopIdle = onIdleTimeout(() => {
      rememberLogoutReason('idle')
      logout()
    })
    return () => {
      stopIdle()
      stopWatch()
    }
  }, [user, logout])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isRestoring,
      isAuthenticated: user !== null,
      login: async (payload: LoginPayload) => applySession(await authApi.login(payload)),
      register: async (payload: RegisterPayload) => applySession(await authApi.register(payload)),
      logout,
    }),
    [applySession, isRestoring, logout, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
