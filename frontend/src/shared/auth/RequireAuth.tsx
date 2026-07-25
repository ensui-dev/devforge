import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * Gate for authenticated routes.
 *
 * Records where the user was heading so signing in returns them there instead of
 * dropping them on the workspace list.
 */
export function RequireAuth() {
  const { isAuthenticated, isInitialising } = useAuth()
  const location = useLocation()

  if (isInitialising) {
    // Nothing is rendered until the stored session has been read, otherwise a
    // reload would briefly redirect an authenticated user to the login screen.
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return <Outlet />
}
