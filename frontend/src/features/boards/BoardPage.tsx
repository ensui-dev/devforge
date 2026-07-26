import { Fragment, useState } from 'react'
import type { DragEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '../../shared/components/Button'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { describeError } from '../../shared/components/describeError'
import { TextField } from '../../shared/components/Field'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import { roleAtLeast, type BoardColumn, type Task } from '../../shared/types'
import { useCurrentWorkspace } from '../workspaces/WorkspaceContext'
import { ColumnHeader } from './ColumnHeader'
import { TaskCard } from './TaskCard'
import { TaskDialog } from './TaskDialog'
import { useAddColumn, useBoard, useDeleteBoard, useMoveTask, useRenameBoard } from './useBoards'
import './BoardPage.css'

interface DragState {
  taskId: string
  fromColumnId: string
}

/** Where a dragged card would land: a column, and an index within it. */
interface DropTarget {
  columnId: string
  index: number
}

export function BoardPage() {
  const workspace = useCurrentWorkspace()
  const { boardId = '' } = useParams()
  const navigate = useNavigate()
  const { notify, notifyError } = useToast()

  const { data: board, isPending, error, refetch } = useBoard(workspace.id, boardId)
  const moveTask = useMoveTask(workspace.id, boardId)
  const addColumn = useAddColumn(workspace.id, boardId)
  const deleteBoard = useDeleteBoard(workspace.id)
  const renameBoard = useRenameBoard(workspace.id, boardId)

  const [drag, setDrag] = useState<DragState | null>(null)
  const [dropTarget, setDropTarget] = useState<DropTarget | null>(null)
  const [taskDialog, setTaskDialog] = useState<{ task: Task | null; columnId: string } | null>(null)
  const [columnDialogOpen, setColumnDialogOpen] = useState(false)
  const [columnName, setColumnName] = useState('')
  const [confirmDeleteBoard, setConfirmDeleteBoard] = useState(false)
  const [renameOpen, setRenameOpen] = useState(false)
  const [boardName, setBoardName] = useState('')

  const canWrite = roleAtLeast(workspace.callerRole, 'MEMBER')
  const canAdmin = roleAtLeast(workspace.callerRole, 'ADMIN')

  if (isPending) {
    return <LoadingState label="Loading board" />
  }

  if (error || !board) {
    return (
      <div className="stack">
        <ErrorState
          title="Could not open this board"
          error={error ?? new Error('Board not found')}
          onRetry={refetch}
        />
        <p>
          <Link to={`/workspaces/${workspace.id}/boards`}>Back to boards</Link>
        </p>
      </div>
    )
  }

  const handleDragStart = (event: DragEvent<HTMLElement>, task: Task) => {
    setDrag({ taskId: task.id, fromColumnId: task.columnId })
    event.dataTransfer.effectAllowed = 'move'
    // Firefox requires data to be set for a drag to start at all.
    event.dataTransfer.setData('text/plain', task.id)
  }

  const handleDragEnd = () => {
    setDrag(null)
    setDropTarget(null)
  }

  const handleDrop = async (columnId: string, index: number) => {
    const active = drag
    setDrag(null)
    setDropTarget(null)

    if (!active) {
      return
    }

    const column = board.columns.find((candidate) => candidate.id === columnId)
    const currentIndex = column?.tasks.findIndex((task) => task.id === active.taskId) ?? -1
    // Dropping a card back where it already sits is not a change.
    if (active.fromColumnId === columnId && (currentIndex === index || currentIndex === index - 1)) {
      return
    }

    try {
      await moveTask.mutateAsync({ taskId: active.taskId, payload: { columnId, position: index } })
    } catch (caught) {
      notifyError(describeError(caught, 'Could not move the task.'))
    }
  }

  const handleAddColumn = async () => {
    try {
      await addColumn.mutateAsync({ name: columnName.trim() })
      notify('Column added')
      setColumnDialogOpen(false)
      setColumnName('')
    } catch (caught) {
      notifyError(describeError(caught, 'Could not add the column.'))
    }
  }

  const handleRenameBoard = async () => {
    try {
      await renameBoard.mutateAsync(boardName.trim())
      notify('Board renamed')
      setRenameOpen(false)
    } catch (caught) {
      notifyError(describeError(caught, 'Could not rename the board.'))
    }
  }

  const handleDeleteBoard = async () => {
    try {
      await deleteBoard.mutateAsync(boardId)
      notify(`Deleted ${board.name}`)
      navigate(`/workspaces/${workspace.id}/boards`)
    } catch (caught) {
      notifyError(describeError(caught, 'Could not delete the board.'))
    }
  }

  const isDropTarget = (columnId: string, index: number) =>
    dropTarget?.columnId === columnId && dropTarget.index === index

  const renderDropZone = (column: BoardColumn, index: number) => (
    <li
      key={`drop-${column.id}-${index}`}
      className={isDropTarget(column.id, index) ? 'drop-zone drop-zone--active' : 'drop-zone'}
      onDragOver={(event) => {
        if (!drag) {
          return
        }
        // Preventing default is what marks this element as a valid drop target.
        event.preventDefault()
        setDropTarget({ columnId: column.id, index })
      }}
      onDrop={(event) => {
        event.preventDefault()
        void handleDrop(column.id, index)
      }}
      aria-hidden="true"
    />
  )

  return (
    <div className="stack">
      <nav className="crumbs" aria-label="Breadcrumb">
        <Link to={`/workspaces/${workspace.id}/boards`}>Boards</Link>
        <span aria-hidden="true">/</span>
        <span className="crumbs__current">{board.name}</span>
      </nav>

      <div className="page-header">
        <div>
          <p className="mono-label">Board</p>
          <h1 className="page-header__title">{board.name}</h1>
          {canWrite ? (
            <p className="page-header__subtitle">
              Drag a card between columns, or open it to move, assign, and link documents.
            </p>
          ) : null}
        </div>
        <div className="page-header__actions">
          {canAdmin ? (
            <Button variant="danger" size="sm" onClick={() => setConfirmDeleteBoard(true)}>
              Delete board
            </Button>
          ) : null}
          {canWrite ? (
            <>
              <Button
                variant="ghost"
                onClick={() => {
                  setBoardName(board.name)
                  setRenameOpen(true)
                }}
              >
                Rename
              </Button>
              <Button variant="secondary" onClick={() => setColumnDialogOpen(true)}>
                Add column
              </Button>
              <Button onClick={() => setTaskDialog({ task: null, columnId: board.columns[0]?.id ?? '' })}>
                New task
              </Button>
            </>
          ) : null}
        </div>
      </div>

      <div className="board" role="list">
        {board.columns.map((column) => (
          <section
            className="column"
            key={column.id}
            role="listitem"
            aria-label={`${column.name}, ${column.tasks.length} tasks`}
          >
            <ColumnHeader
              workspaceId={workspace.id}
              boardId={board.id}
              board={board}
              column={column}
              canWrite={canWrite}
            />

            <ul
              className="column__tasks"
              onDragOver={(event) => {
                // Keeps the drop target on the trailing zone when the pointer is
                // over the column's empty space.
                if (drag) {
                  event.preventDefault()
                }
              }}
            >
              {column.tasks.map((task, index) => (
                <Fragment key={task.id}>
                  {renderDropZone(column, index)}
                  <li>
                    <TaskCard
                      task={task}
                      draggable={canWrite}
                      dragging={drag?.taskId === task.id}
                      onOpen={() => setTaskDialog({ task, columnId: column.id })}
                      onDragStart={(event) => handleDragStart(event, task)}
                      onDragEnd={handleDragEnd}
                    />
                  </li>
                </Fragment>
              ))}
              {renderDropZone(column, column.tasks.length)}

              {column.tasks.length === 0 ? (
                <li className="column__empty">
                  {canWrite ? 'Drop a task here' : 'Nothing here yet'}
                </li>
              ) : null}
            </ul>

            {canWrite ? (
              <button
                type="button"
                className="column__add"
                onClick={() => setTaskDialog({ task: null, columnId: column.id })}
              >
                + Add task
              </button>
            ) : null}
          </section>
        ))}
      </div>

      {taskDialog ? (
        <TaskDialog
          workspaceId={workspace.id}
          board={board}
          task={
            // Re-read from the board so the dialog shows fresh data after a link
            // or unlink, rather than the snapshot taken when it opened.
            taskDialog.task
              ? (board.columns
                  .flatMap((column) => column.tasks)
                  .find((task) => task.id === taskDialog.task?.id) ?? taskDialog.task)
              : null
          }
          defaultColumnId={taskDialog.columnId}
          open
          canWrite={canWrite}
          onClose={() => setTaskDialog(null)}
        />
      ) : null}

      <Modal
        title="Rename board"
        open={renameOpen}
        onClose={() => setRenameOpen(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRenameOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleRenameBoard}
              loading={renameBoard.isPending}
              disabled={!boardName.trim() || boardName.trim() === board.name}
            >
              Save name
            </Button>
          </>
        }
      >
        <TextField
          label="Name"
          value={boardName}
          autoFocus
          onChange={(event) => setBoardName(event.target.value)}
        />
      </Modal>

      <Modal
        title="Add a column"
        open={columnDialogOpen}
        onClose={() => setColumnDialogOpen(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setColumnDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleAddColumn} loading={addColumn.isPending} disabled={!columnName.trim()}>
              Add column
            </Button>
          </>
        }
      >
        <TextField
          label="Name"
          value={columnName}
          autoFocus
          hint="Added at the end of the board."
          onChange={(event) => setColumnName(event.target.value)}
        />
      </Modal>

      <Modal
        title="Delete this board?"
        open={confirmDeleteBoard}
        onClose={() => setConfirmDeleteBoard(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDeleteBoard(false)}>
              Keep it
            </Button>
            <Button variant="danger" onClick={handleDeleteBoard} loading={deleteBoard.isPending}>
              Delete board
            </Button>
          </>
        }
      >
        <p>
          <strong>{board.name}</strong> and every task on it will be removed. Linked documents are
          not affected. This cannot be undone.
        </p>
      </Modal>
    </div>
  )
}
