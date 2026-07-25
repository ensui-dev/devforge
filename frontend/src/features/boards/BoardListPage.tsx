import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../../shared/components/Button'
import { EmptyState } from '../../shared/components/EmptyState'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { describeError } from '../../shared/components/describeError'
import { TextField } from '../../shared/components/Field'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import { roleAtLeast } from '../../shared/types'
import { formatRelative } from '../../shared/utils/slugify'
import { useCurrentWorkspace } from '../workspaces/WorkspaceContext'
import { useBoardList, useCreateBoard } from './useBoards'
import './BoardListPage.css'

export function BoardListPage() {
  const workspace = useCurrentWorkspace()
  const navigate = useNavigate()
  const { notify } = useToast()

  const { data: boards, isPending, error, refetch } = useBoardList(workspace.id)
  const createBoard = useCreateBoard(workspace.id)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const [createError, setCreateError] = useState<unknown>(null)

  const canWrite = roleAtLeast(workspace.callerRole, 'MEMBER')

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setCreateError(null)
    try {
      const board = await createBoard.mutateAsync(name.trim())
      notify(`Created ${board.name}`)
      setDialogOpen(false)
      setName('')
      navigate(`/workspaces/${workspace.id}/boards/${board.id}`)
    } catch (caught) {
      setCreateError(caught)
    }
  }

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <p className="mono-label">Boards</p>
          <h1 className="page-header__title">Delivery</h1>
          <p className="page-header__subtitle">
            Track work in columns, and attach the documents each task depends on.
          </p>
        </div>
        {canWrite ? (
          <div className="page-header__actions">
            <Button onClick={() => setDialogOpen(true)}>New board</Button>
          </div>
        ) : null}
      </div>

      {isPending ? <LoadingState label="Loading boards" /> : null}
      {error ? <ErrorState error={error} onRetry={refetch} /> : null}

      {boards && boards.length === 0 ? (
        <EmptyState
          title="No boards yet"
          description="A new board starts with Backlog, In Progress, Review, and Done. Rename or reorder them once it exists."
          action={canWrite ? <Button onClick={() => setDialogOpen(true)}>Create board</Button> : undefined}
        />
      ) : null}

      {boards && boards.length > 0 ? (
        <ul className="board-grid">
          {boards.map((board) => (
            <li key={board.id}>
              <Link className="board-card" to={`/workspaces/${workspace.id}/boards/${board.id}`}>
                <h2 className="board-card__name">{board.name}</h2>
                <dl className="board-card__stats">
                  <div>
                    <dt className="mono-label">Columns</dt>
                    <dd className="board-card__figure">{board.columnCount}</dd>
                  </div>
                  <div>
                    <dt className="mono-label">Tasks</dt>
                    <dd className="board-card__figure">{board.taskCount}</dd>
                  </div>
                </dl>
                <p className="board-card__meta">Updated {formatRelative(board.updatedAt)}</p>
              </Link>
            </li>
          ))}
        </ul>
      ) : null}

      <Modal
        title="New board"
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="create-board" loading={createBoard.isPending}>
              Create board
            </Button>
          </>
        }
      >
        <form id="create-board" onSubmit={handleCreate} noValidate>
          <div className="stack">
            {createError ? (
              <p className="form-error" role="alert">
                {describeError(createError, 'Could not create the board.')}
              </p>
            ) : null}
            <TextField
              label="Name"
              required
              autoFocus
              value={name}
              hint="For example: Platform delivery, or Q3 migration."
              onChange={(event) => setName(event.target.value)}
            />
          </div>
        </form>
      </Modal>
    </div>
  )
}
