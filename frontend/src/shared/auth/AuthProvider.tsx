import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { configureApi } from '../api/client'
import { authApi } from '../api/endpoints'
import type { AuthenticationResult, LoginPayload, RegisterPayload, User } from '../types'
import { AuthContext } from './AuthContext'
import type { AuthContextValue } from './AuthContext'

const STORAGE_KEY = 'devforge.session'

interface StoredSession {
  accessToken: string
  expiresAt: string
  user: User
}

function readStoredSession(): StoredSession | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const session = JSON.parse(raw) as StoredSession
    // Discard an expired token locally rather than waiting for the first 401.
    if (!session.accessToken || new Date(session.expiresAt).getTime() <= Date.now()) {
      window.localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    window.localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

/**
 * Holds the session and keeps the API client's token in sync.
 *
 * The token is mirrored into a ref because {@link configureApi} reads it
 * synchronously during a request; reading state directly would capture a stale
 * value in the closure.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<StoredSession | null>(null)
  const [isInitialising, setIsInitialising] = useState(true)
  const tokenRef = useRef<string | null>(null)

  const clearSession = useCallback(() => {
    tokenRef.current = null
    window.localStorage.removeItem(STORAGE_KEY)
    setSession(null)
  }, [])

  const storeSession = useCallback((result: AuthenticationResult) => {
    const next: StoredSession = {
      accessToken: result.accessToken,
      expiresAt: result.expiresAt,
      user: result.user,
    }
    tokenRef.current = next.accessToken
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setSession(next)
  }, [])

  // Wired before the first render completes, so no request can be issued without
  // its token attached.
  useMemo(() => {
    configureApi({
      getToken: () => tokenRef.current,
      onUnauthorized: () => {
        tokenRef.current = null
        window.localStorage.removeItem(STORAGE_KEY)
        setSession(null)
      },
    })
  }, [])

  useEffect(() => {
    const stored = readStoredSession()
    if (stored) {
      tokenRef.current = stored.accessToken
      setSession(stored)
    }
    setIsInitialising(false)
  }, [])

  const logIn = useCallback(
    async (payload: LoginPayload) => {
      storeSession(await authApi.login(payload))
    },
    [storeSession],
  )

  const register = useCallback(
    async (payload: RegisterPayload) => {
      storeSession(await authApi.register(payload))
    },
    [storeSession],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      isInitialising,
      logIn,
      register,
      logOut: clearSession,
    }),
    [session, isInitialising, logIn, register, clearSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
