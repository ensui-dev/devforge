import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { documentApi, memberApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { DOCUMENT_PICKER_SIZE } from '../documents/useDocuments'
import { ApiError } from '../../shared/api/client'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { SelectField, TextAreaField, TextField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import {
  DOCUMENT_TYPE_LABELS,
  TASK_PRIORITIES,
  type Board,
  type Task,
  type TaskPriority,
} from '../../shared/types'
import {
  useCreateTask,
  useDeleteTask,
  useLinkTaskDocument,
  useMoveTask,
  useUnlinkTaskDocument,
  useUpdateTask,
} from './useBoards'
import './TaskDialog.css'

interface TaskDialogProps {
  workspaceId: string
  board: Board
  /** The task being edited, or null when creating. */
  task: Task | null
  /** Column a new task should land in. */
  defaultColumnId: string
  open: boolean
  canWrite: boolean
  onClose: () => void
}

export function TaskDialog({
  workspaceId,
  board,
  task,
  defaultColumnId,
  open,
  canWrite,
  onClose,
}: TaskDialogProps) {
  const createTask = useCreateTask(workspaceId, board.id)
  const updateTask = useUpdateTask(workspaceId, board.id)
  const deleteTask = useDeleteTask(workspaceId, board.id)
  const moveTask = useMoveTask(workspaceId, board.id)
  const linkDocument = useLinkTaskDocument(workspaceId, board.id)
  const unlinkDocument = useUnlinkTaskDocument(workspaceId, board.id)
  const { notify, notifyError } = useToast()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM')
  const [assigneeId, setAssigneeId] = useState('')
  const [columnId, setColumnId] = useState(defaultColumnId)
  const [documentToLink, setDocumentToLink] = useState('')
  const [error, setError] = useState<unknown>(null)

  const { data: members = [] } = useQuery({
    queryKey: queryKeys.members.all(workspaceId),
    queryFn: () => memberApi.list(workspaceId),
    enabled: open,
  })

  const { data: documents } = useQuery({
    queryKey: queryKeys.documents.list(workspaceId, 'ALL', 0, DOCUMENT_PICKER_SIZE),
    queryFn: () => documentApi.list(workspaceId, { size: DOCUMENT_PICKER_SIZE }),
    enabled: open,
  })

  useEffect(() => {
    if (!open) {
      return
    }
    setTitle(task?.title ?? '')
    setDescription(task?.description ?? '')
    setPriority(task?.priority ?? 'MEDIUM')
    setAssigneeId(task?.assignee?.id ?? '')
    setColumnId(task?.columnId ?? defaultColumnId)
    setDocumentToLink('')
    setError(null)
  }, [open, task, defaultColumnId])

  const linkedIds = useMemo(
    () => new Set((task?.linkedDocuments ?? []).map((document) => document.id)),
    [task],
  )

  const linkableDocuments = (documents?.content ?? []).filter(
    (document) => !linkedIds.has(document.id),
  )

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)

    try {
      if (task) {
        await updateTask.mutateAsync({
          taskId: task.id,
          payload: {
            title: title.trim(),
            description: description.trim() || undefined,
            priority,
            assigneeId: assigneeId || null,
          },
        })
        // Column changes go through the move endpoint, which owns positions.
        if (columnId !== task.columnId) {
          await moveTask.mutateAsync({ taskId: task.id, payload: { columnId, position: 0 } })
        }
        notify('Task saved')
      } else {
        await createTask.mutateAsync({
          title: title.trim(),
          description: description.trim() || undefined,
          columnId,
          priority,
          assigneeId: assigneeId || null,
        })
        notify('Task created')
      }
      onClose()
    } catch (caught) {
      setError(caught)
    }
  }

  const handleDelete = async () => {
    if (!task) {
      return
    }
    try {
      await deleteTask.mutateAsync(task.id)
      notify('Task deleted')
      onClose()
    } catch (caught) {
      notifyError(describeError(caught, 'Could not delete the task.'))
    }
  }

  const handleLink = async () => {
    if (!task || !documentToLink) {
      return
    }
    try {
      await linkDocument.mutateAsync({ taskId: task.id, documentId: documentToLink })
      setDocumentToLink('')
      notify('Document linked')
    } catch (caught) {
      notifyError(describeError(caught, 'Could not link the document.'))
    }
  }

  const handleUnlink = async (documentId: string) => {
    if (!task) {
      return
    }
    try {
      await unlinkDocument.mutateAsync({ taskId: task.id, documentId })
      notify('Document unlinked')
    } catch (caught) {
      notifyError(describeError(caught, 'Could not unlink the document.'))
    }
  }

  const fieldError = (field: string) =>
    error instanceof ApiError ? error.fieldError(field) : undefined

  const saving = createTask.isPending || updateTask.isPending || moveTask.isPending

  return (
    <Modal
      title={task ? 'Task details' : 'New task'}
      open={open}
      onClose={onClose}
      width="lg"
      footer={
        <>
          {task && canWrite ? (
            <Button variant="danger" size="sm" onClick={handleDelete} loading={deleteTask.isPending}>
              Delete task
            </Button>
          ) : null}
          <span className="spacer" />
          <Button variant="ghost" onClick={onClose}>
            {canWrite ? 'Cancel' : 'Close'}
          </Button>
          {canWrite ? (
            <Button type="submit" form="task-form" loading={saving}>
              {task ? 'Save changes' : 'Create task'}
            </Button>
          ) : null}
        </>
      }
    >
      <form id="task-form" onSubmit={handleSubmit} noValidate>
        <div className="stack">
          {error && !(error instanceof ApiError && Object.keys(error.fieldErrors).length) ? (
            <p className="form-error" role="alert">
              {describeError(error, 'Could not save the task.')}
            </p>
          ) : null}

          <TextField
            label="Title"
            required
            autoFocus
            disabled={!canWrite}
            value={title}
            error={fieldError('title')}
            onChange={(event) => setTitle(event.target.value)}
          />

          <div className="task-dialog__row">
            {/* Doubles as the keyboard-accessible way to move a task, so ordering
                is not drag-only. */}
            <SelectField
              label="Column"
              value={columnId}
              disabled={!canWrite}
              onChange={(event) => setColumnId(event.target.value)}
            >
              {board.columns.map((column) => (
                <option key={column.id} value={column.id}>
                  {column.name}
                </option>
              ))}
            </SelectField>

            <SelectField
              label="Priority"
              value={priority}
              disabled={!canWrite}
              onChange={(event) => setPriority(event.target.value as TaskPriority)}
            >
              {TASK_PRIORITIES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </SelectField>

            <SelectField
              label="Assignee"
              value={assigneeId}
              disabled={!canWrite}
              error={fieldError('assigneeId')}
              onChange={(event) => setAssigneeId(event.target.value)}
            >
              <option value="">Unassigned</option>
              {members.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.displayName}
                </option>
              ))}
            </SelectField>
          </div>

          <TextAreaField
            label="Description"
            rows={5}
            disabled={!canWrite}
            value={description}
            error={fieldError('description')}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>
      </form>

      {/* Document citations are edited outside the form: each one is an immediate
          action, not part of the save. */}
      {task ? (
        <section className="task-docs">
          <h3 className="mono-label">Linked documents</h3>

          {task.linkedDocuments.length === 0 ? (
            <p className="task-docs__empty">
              Nothing linked yet. Point this task at the spec or runbook it depends on.
            </p>
          ) : (
            <ul className="task-docs__list">
              {task.linkedDocuments.map((document) => (
                <li key={document.id} className="task-docs__item">
                  <Badge tone="trace">{DOCUMENT_TYPE_LABELS[document.documentType]}</Badge>
                  <a
                    className="task-docs__link"
                    href={`/workspaces/${workspaceId}/documents/${document.id}`}
                  >
                    {document.title}
                  </a>
                  {canWrite ? (
                    <button
                      type="button"
                      className="task-docs__remove"
                      onClick={() => handleUnlink(document.id)}
                      aria-label={`Unlink ${document.title}`}
                    >
                      &times;
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}

          {canWrite && linkableDocuments.length > 0 ? (
            <div className="task-docs__add">
              <label className="visually-hidden" htmlFor="task-link-document">
                Document to link
              </label>
              <select
                id="task-link-document"
                className="field__control field__control--select"
                value={documentToLink}
                onChange={(event) => setDocumentToLink(event.target.value)}
              >
                <option value="">Choose a document…</option>
                {linkableDocuments.map((document) => (
                  <option key={document.id} value={document.id}>
                    {document.title}
                  </option>
                ))}
              </select>
              <Button
                variant="secondary"
                size="sm"
                onClick={handleLink}
                disabled={!documentToLink}
                loading={linkDocument.isPending}
              >
                Link
              </Button>
            </div>
          ) : null}
        </section>
      ) : null}
    </Modal>
  )
}
