import { useState } from 'react'
import { Button } from '../../shared/components/Button'
import { describeError } from '../../shared/components/describeError'
import { TextField } from '../../shared/components/Field'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import type { Board, BoardColumn } from '../../shared/types'
import { useMoveColumn, useRemoveColumn, useUpdateColumn } from './useBoards'
import './ColumnHeader.css'

interface ColumnHeaderProps {
  workspaceId: string
  boardId: string
  board: Board
  column: BoardColumn
  canWrite: boolean
}

export function ColumnHeader({ workspaceId, boardId, board, column, canWrite }: ColumnHeaderProps) {
  const updateColumn = useUpdateColumn(workspaceId, boardId)
  const moveColumn = useMoveColumn(workspaceId, boardId)
  const removeColumn = useRemoveColumn(workspaceId, boardId)
  const { notify, notifyError } = useToast()

  const [settingsOpen, setSettingsOpen] = useState(false)
  const [name, setName] = useState(column.name)
  const [wipLimit, setWipLimit] = useState(column.wipLimit?.toString() ?? '')
  const [confirmDelete, setConfirmDelete] = useState(false)

  const overLimit = column.wipLimit !== null && column.tasks.length > column.wipLimit
  const atLimit = column.wipLimit !== null && column.tasks.length === column.wipLimit

  const openSettings = () => {
    setName(column.name)
    setWipLimit(column.wipLimit?.toString() ?? '')
    setSettingsOpen(true)
  }

  const handleSave = async () => {
    const parsed = wipLimit.trim() === '' ? null : Number(wipLimit)
    if (parsed !== null && (!Number.isInteger(parsed) || parsed < 1)) {
      notifyError('The work-in-progress limit must be a whole number of 1 or more.')
      return
    }

    try {
      await updateColumn.mutateAsync({
        columnId: column.id,
        payload: { name: name.trim(), wipLimit: parsed },
      })
      notify('Column updated')
      setSettingsOpen(false)
    } catch (error) {
      notifyError(describeError(error, 'Could not update the column.'))
    }
  }

  const handleMove = async (direction: -1 | 1) => {
    try {
      await moveColumn.mutateAsync({ columnId: column.id, position: column.position + direction })
    } catch (error) {
      notifyError(describeError(error, 'Could not move the column.'))
    }
  }

  const handleDelete = async () => {
    try {
      await removeColumn.mutateAsync(column.id)
      notify(`Deleted ${column.name}`)
      setConfirmDelete(false)
      setSettingsOpen(false)
    } catch (error) {
      notifyError(describeError(error, 'Could not delete the column.'))
    }
  }

  return (
    <>
      <header className="column__header">
        <div className="column__identity">
          <h2 className="column__name">{column.name}</h2>
          <span
            className={
              overLimit
                ? 'column__count column__count--over'
                : atLimit
                  ? 'column__count column__count--at'
                  : 'column__count'
            }
            title={column.wipLimit ? `Work-in-progress limit: ${column.wipLimit}` : undefined}
          >
            {column.tasks.length}
            {column.wipLimit !== null ? `/${column.wipLimit}` : ''}
          </span>
        </div>

        {canWrite ? (
          <div className="column__controls">
            <button
              type="button"
              className="column__control"
              onClick={() => handleMove(-1)}
              disabled={column.position === 0 || moveColumn.isPending}
              aria-label={`Move ${column.name} left`}
            >
              ‹
            </button>
            <button
              type="button"
              className="column__control"
              onClick={() => handleMove(1)}
              disabled={column.position === board.columns.length - 1 || moveColumn.isPending}
              aria-label={`Move ${column.name} right`}
            >
              ›
            </button>
            <button
              type="button"
              className="column__control"
              onClick={openSettings}
              aria-label={`${column.name} settings`}
            >
              ⋯
            </button>
          </div>
        ) : null}
      </header>

      <Modal
        title={`${column.name} settings`}
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        width="sm"
        footer={
          <>
            <Button
              variant="danger"
              size="sm"
              onClick={() => setConfirmDelete(true)}
              disabled={board.columns.length <= 1}
              title={
                board.columns.length <= 1 ? 'A board must keep at least one column' : undefined
              }
            >
              Delete column
            </Button>
            <span className="spacer" />
            <Button variant="ghost" onClick={() => setSettingsOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleSave} loading={updateColumn.isPending}>
              Save
            </Button>
          </>
        }
      >
        <TextField label="Name" value={name} onChange={(event) => setName(event.target.value)} />
        <TextField
          label="Work-in-progress limit"
          type="number"
          min={1}
          value={wipLimit}
          hint="Leave blank for no limit. Tasks cannot be added or moved in beyond it."
          onChange={(event) => setWipLimit(event.target.value)}
        />
      </Modal>

      <Modal
        title="Delete this column?"
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Keep it
            </Button>
            <Button variant="danger" onClick={handleDelete} loading={removeColumn.isPending}>
              Delete column
            </Button>
          </>
        }
      >
        <p>
          {column.tasks.length > 0
            ? `${column.name} and its ${column.tasks.length} task${column.tasks.length === 1 ? '' : 's'} will be removed. Move anything you want to keep first.`
            : `${column.name} will be removed.`}
        </p>
      </Modal>
    </>
  )
}
