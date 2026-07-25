import { createContext } from 'react'
import type { LoginPayload, RegisterPayload, User } from '../types'

export interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  /** True until the stored session has been read, so routes do not flash. */
  isInitialising: boolean
  logIn: (payload: LoginPayload) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logOut: () => void
}

/**
 * Declared apart from the provider component so that neither this file nor the
 * provider mixes component and non-component exports, which would break React's
 * fast refresh.
 */
export const AuthContext = createContext<AuthContextValue | null>(null)
