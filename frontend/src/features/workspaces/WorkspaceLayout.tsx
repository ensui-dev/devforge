import { Link, NavLink, Outlet, useParams } from 'react-router-dom'
import { useAuth } from '../../shared/auth/useAuth'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { WorkspaceContext } from './WorkspaceContext'
import { useWorkspace } from './useWorkspaces'
import './WorkspaceLayout.css'

const NAV_ITEMS = [
  { to: '', label: 'Overview', end: true },
  { to: 'documents', label: 'Documents', end: false },
  { to: 'boards', label: 'Boards', end: false },
  { to: 'members', label: 'Team', end: false },
]

export function WorkspaceLayout() {
  const { workspaceId = '' } = useParams()
  const { data: workspace, isPending, error, refetch } = useWorkspace(workspaceId)
  const { user, logOut } = useAuth()

  if (isPending) {
    return (
      <div className="workspace-shell__loading">
        <LoadingState label="Loading workspace" />
      </div>
    )
  }

  if (error || !workspace) {
    return (
      <div className="workspace-shell__loading">
        <ErrorState
          title="Could not open this workspace"
          error={error ?? new Error('Workspace not found')}
          onRetry={refetch}
        />
        <p>
          <Link to="/">Back to your workspaces</Link>
        </p>
      </div>
    )
  }

  return (
    <WorkspaceContext.Provider value={workspace}>
      <div className="workspace-shell">
        <aside className="rail">
          <Link className="rail__brand" to="/">
            <span className="rail__mark" aria-hidden="true">
              ⌁
            </span>
            <span className="rail__wordmark">DevForge</span>
          </Link>

          <div className="rail__workspace">
            <p className="rail__slug">/{workspace.slug}</p>
            <p className="rail__name">{workspace.name}</p>
            <Badge tone={workspace.callerRole === 'VIEWER' ? 'neutral' : 'trace'}>
              {workspace.callerRole}
            </Badge>
          </div>

          <nav className="rail__nav" aria-label="Workspace sections">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.label}
                to={item.to}
                end={item.end}
                className={({ isActive }) => (isActive ? 'rail__link rail__link--active' : 'rail__link')}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="rail__footer">
            <p className="rail__user">{user?.displayName}</p>
            <Button variant="ghost" size="sm" onClick={logOut}>
              Sign out
            </Button>
          </div>
        </aside>

        <main className="workspace-shell__content">
          <Outlet />
        </main>
      </div>
    </WorkspaceContext.Provider>
  )
}
