import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../shared/auth/useAuth'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { EmptyState } from '../../shared/components/EmptyState'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { formatRelative } from '../../shared/utils/slugify'
import { CreateWorkspaceDialog } from './CreateWorkspaceDialog'
import { useWorkspaces } from './useWorkspaces'
import './WorkspaceListPage.css'

export function WorkspaceListPage() {
  const { data: workspaces, isPending, error, refetch } = useWorkspaces()
  const { user, logOut } = useAuth()
  const navigate = useNavigate()
  const [dialogOpen, setDialogOpen] = useState(false)

  return (
    <div className="workspaces">
      <header className="workspaces__header">
        <div className="row">
          <span className="workspaces__mark" aria-hidden="true">
            ⌁
          </span>
          <span className="workspaces__wordmark">DevForge</span>
        </div>
        <div className="row">
          <span className="workspaces__user">{user?.displayName}</span>
          <Button variant="ghost" size="sm" onClick={logOut}>
            Sign out
          </Button>
        </div>
      </header>

      <main className="workspaces__main">
        <div className="page-header">
          <div>
            <p className="mono-label">Workspaces</p>
            <h1 className="page-header__title">Your projects</h1>
            <p className="page-header__subtitle">
              Each workspace holds its own documentation, boards, and team.
            </p>
          </div>
          <div className="page-header__actions">
            <Button onClick={() => setDialogOpen(true)}>New workspace</Button>
          </div>
        </div>

        {isPending ? <LoadingState label="Loading workspaces" /> : null}

        {error ? <ErrorState error={error} onRetry={refetch} /> : null}

        {workspaces && workspaces.length === 0 ? (
          <EmptyState
            title="No workspaces yet"
            description="Create a workspace to start documenting architecture, procedures, and the stack — then track the work that changes them."
            action={<Button onClick={() => setDialogOpen(true)}>Create workspace</Button>}
          />
        ) : null}

        {workspaces && workspaces.length > 0 ? (
          <ul className="workspace-grid">
            {workspaces.map((workspace) => (
              <li key={workspace.id}>
                <Link className="workspace-card" to={`/workspaces/${workspace.id}`}>
                  <div className="row row--between">
                    <span className="workspace-card__slug">/{workspace.slug}</span>
                    <Badge tone={workspace.callerRole === 'VIEWER' ? 'neutral' : 'trace'}>
                      {workspace.callerRole}
                    </Badge>
                  </div>
                  <h2 className="workspace-card__name">{workspace.name}</h2>
                  {workspace.description ? (
                    <p className="workspace-card__description">{workspace.description}</p>
                  ) : null}
                  <p className="workspace-card__meta">
                    Updated {formatRelative(workspace.updatedAt)}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        ) : null}
      </main>

      <CreateWorkspaceDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onCreated={(workspaceId) => {
          setDialogOpen(false)
          navigate(`/workspaces/${workspaceId}`)
        }}
      />
    </div>
  )
}
