import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { AuthContext, type AuthContextValue } from '@/features/auth/AuthContext'
import { authApi, type LoginPayload, type RegisterPayload } from '@/features/auth/authApi'
import { onSessionExpired } from '@/lib/api'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { clearSession, readSession, writeSession } from '@/lib/session'
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
      let lastError: unknown
      for (let attempt = 0; attempt < 4; attempt++) {
        try {
          const profile = await authApi.me()
          if (!cancelled) setUser(profile)
          return
        } catch (err) {
          lastError = err
          if (err instanceof ApiRequestError && err.status === 401) {
            if (!cancelled) {
              clearSession()
              setUser(null)
            }
            return
          }
          await new Promise((r) => setTimeout(r, 500 * (attempt + 1)))
        }
      }
      if (!cancelled) {
        console.warn('Could not restore session after retries', lastError)
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
