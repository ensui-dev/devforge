import { createContext } from 'react'

export interface ToastContextValue {
  /** Confirms a completed action, phrased in the past tense. */
  notify: (message: string) => void
  /** Reports a failure. Says what went wrong; never apologises. */
  notifyError: (message: string) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)
