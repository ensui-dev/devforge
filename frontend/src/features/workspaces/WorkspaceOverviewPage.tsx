import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { boardApi, documentApi, memberApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { DOCUMENT_TYPE_LABELS, roleAtLeast } from '../../shared/types'
import { formatRelative } from '../../shared/utils/slugify'
import { useCurrentWorkspace } from './WorkspaceContext'
import './WorkspaceOverviewPage.css'

export function WorkspaceOverviewPage() {
  const workspace = useCurrentWorkspace()

  const documents = useQuery({
    queryKey: queryKeys.documents.list(workspace.id, 'ALL', 0),
    queryFn: () => documentApi.list(workspace.id, { size: 5 }),
  })

  const boards = useQuery({
    queryKey: queryKeys.boards.all(workspace.id),
    queryFn: () => boardApi.list(workspace.id),
  })

  const members = useQuery({
    queryKey: queryKeys.members.all(workspace.id),
    queryFn: () => memberApi.list(workspace.id),
  })

  const canWrite = roleAtLeast(workspace.callerRole, 'MEMBER')
  const openTasks = (boards.data ?? []).reduce((total, board) => total + board.taskCount, 0)

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <p className="mono-label">Overview</p>
          <h1 className="page-header__title">{workspace.name}</h1>
          {workspace.description ? (
            <p className="page-header__subtitle">{workspace.description}</p>
          ) : null}
        </div>
      </div>

      {/* Counts in mono, labelled below the figure, so the three read as one
          instrument panel rather than three cards. */}
      <dl className="overview-stats">
        <div className="overview-stat">
          <dd className="overview-stat__figure">{documents.data?.totalElements ?? '—'}</dd>
          <dt className="mono-label">Documents</dt>
        </div>
        <div className="overview-stat">
          <dd className="overview-stat__figure">{boards.data?.length ?? '—'}</dd>
          <dt className="mono-label">Boards</dt>
        </div>
        <div className="overview-stat">
          <dd className="overview-stat__figure">{boards.data ? openTasks : '—'}</dd>
          <dt className="mono-label">Tasks</dt>
        </div>
        <div className="overview-stat">
          <dd className="overview-stat__figure">{members.data?.length ?? '—'}</dd>
          <dt className="mono-label">Members</dt>
        </div>
      </dl>

      <div className="overview-columns">
        <section className="overview-panel">
          <header className="overview-panel__head">
            <h2 className="overview-panel__title">Recent documents</h2>
            <Link className="overview-panel__link" to={`/workspaces/${workspace.id}/documents`}>
              All documents
            </Link>
          </header>

          {documents.data && documents.data.content.length > 0 ? (
            <ul className="overview-list">
              {documents.data.content.map((document) => (
                <li key={document.id}>
                  <Link
                    className="overview-item"
                    to={`/workspaces/${workspace.id}/documents/${document.id}`}
                  >
                    <div className="row">
                      <Badge tone="trace">{DOCUMENT_TYPE_LABELS[document.documentType]}</Badge>
                      <span className="overview-item__title">{document.title}</span>
                    </div>
                    <span className="overview-item__meta">
                      {formatRelative(document.updatedAt)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <div className="overview-empty">
              <p>No documents yet.</p>
              {canWrite ? (
                <Link to={`/workspaces/${workspace.id}/documents`}>
                  <Button variant="secondary" size="sm">
                    Add the first one
                  </Button>
                </Link>
              ) : null}
            </div>
          )}
        </section>

        <section className="overview-panel">
          <header className="overview-panel__head">
            <h2 className="overview-panel__title">Boards</h2>
            <Link className="overview-panel__link" to={`/workspaces/${workspace.id}/boards`}>
              All boards
            </Link>
          </header>

          {boards.data && boards.data.length > 0 ? (
            <ul className="overview-list">
              {boards.data.map((board) => (
                <li key={board.id}>
                  <Link
                    className="overview-item"
                    to={`/workspaces/${workspace.id}/boards/${board.id}`}
                  >
                    <span className="overview-item__title">{board.name}</span>
                    <span className="overview-item__meta">
                      {board.taskCount} task{board.taskCount === 1 ? '' : 's'}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <div className="overview-empty">
              <p>No boards yet.</p>
              {canWrite ? (
                <Link to={`/workspaces/${workspace.id}/boards`}>
                  <Button variant="secondary" size="sm">
                    Create a board
                  </Button>
                </Link>
              ) : null}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
