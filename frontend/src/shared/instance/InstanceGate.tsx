import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useInstance } from './useInstance'

/**
 * Sends a fresh deployment to its setup screen, and keeps a configured one away
 * from it.
 *
 * <p>The redirect is one-way in both directions on purpose. An instance nobody
 * has claimed has nothing worth showing, and an instance that has been claimed
 * must not present a setup form at all — the endpoint behind it already refuses,
 * but a form that cannot succeed is worse than no form.
 */
export function InstanceGate({ children }: { children: ReactNode }) {
  const { instance, isLoading } = useInstance()
  const { pathname } = useLocation()
  const onSetup = pathname === '/setup'

  if (isLoading) {
    // Nothing is shown rather than the wrong thing: routing on a guessed answer
    // would flash the homepage of an instance that has no name yet.
    return (
      <div className="boot" role="status" aria-live="polite">
        <span className="visually-hidden">Loading</span>
      </div>
    )
  }

  if (!instance.configured && !onSetup) {
    return <Navigate to="/setup" replace />
  }

  if (instance.configured && onSetup) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
