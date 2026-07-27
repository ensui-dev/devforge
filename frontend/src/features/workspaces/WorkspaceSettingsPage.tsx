import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../shared/api/client'
import { Button } from '../../shared/components/Button'
import { TextAreaField, TextField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import { roleAtLeast } from '../../shared/types'
import { slugify } from '../../shared/utils/slugify'
import { GitSyncPanel } from '../sync/GitSyncPanel'
import { PublicationPanel } from './PublicationPanel'
import { useCurrentWorkspace } from './WorkspaceContext'
import { useDeleteWorkspace, useUpdateWorkspace } from './useWorkspaces'
import './WorkspaceSettingsPage.css'

export function WorkspaceSettingsPage() {
  const workspace = useCurrentWorkspace()
  const navigate = useNavigate()
  const { notify, notifyError } = useToast()

  const updateWorkspace = useUpdateWorkspace(workspace.id)
  const deleteWorkspace = useDeleteWorkspace()

  const [name, setName] = useState(workspace.name)
  const [slug, setSlug] = useState(workspace.slug)
  const [description, setDescription] = useState(workspace.description ?? '')
  const [error, setError] = useState<unknown>(null)

  const [confirmOpen, setConfirmOpen] = useState(false)
  // Deleting a workspace destroys every document and board in it, so the name
  // must be typed out rather than confirmed with a single click.
  const [confirmation, setConfirmation] = useState('')

  const canEdit = roleAtLeast(workspace.callerRole, 'ADMIN')
  const canDelete = roleAtLeast(workspace.callerRole, 'OWNER')

  // Re-seed when the workspace loads or is changed from elsewhere.
  useEffect(() => {
    setName(workspace.name)
    setSlug(workspace.slug)
    setDescription(workspace.description ?? '')
  }, [workspace])

  const dirty =
    name !== workspace.name ||
    slug !== workspace.slug ||
    description !== (workspace.description ?? '')

  const handleSave = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      const saved = await updateWorkspace.mutateAsync({
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined,
      })
      notify(`Saved ${saved.name}`)
    } catch (caught) {
      setError(caught)
    }
  }

  const handleDelete = async () => {
    try {
      await deleteWorkspace.mutateAsync(workspace.id)
      notify(`Deleted ${workspace.name}`)
      navigate('/app', { replace: true })
    } catch (caught) {
      notifyError(describeError(caught, 'Could not delete the workspace.'))
    }
  }

  const fieldError = (field: string) =>
    error instanceof ApiError ? error.fieldError(field) : undefined

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <p className="mono-label">Settings</p>
          <h1 className="page-header__title">Workspace settings</h1>
          <p className="page-header__subtitle">
            {canEdit
              ? 'Rename this workspace or change the slug used in its links.'
              : 'Only admins and owners can change these details.'}
          </p>
        </div>
      </div>

      <form className="settings-panel" onSubmit={handleSave} noValidate>
        {error && !(error instanceof ApiError && Object.keys(error.fieldErrors).length) ? (
          <p className="form-error" role="alert">
            {describeError(error, 'Could not save the workspace.')}
          </p>
        ) : null}

        <TextField
          label="Name"
          required
          disabled={!canEdit}
          value={name}
          error={fieldError('name')}
          onChange={(event) => setName(event.target.value)}
        />

        <TextField
          label="URL slug"
          required
          disabled={!canEdit}
          value={slug}
          hint="Lowercase letters, numbers, and hyphens."
          error={fieldError('slug')}
          onChange={(event) => setSlug(event.target.value)}
        />

        {canEdit && slug !== slugify(name) && slugify(name) !== '' ? (
          <p className="settings-panel__suggestion">
            Suggested from the name:{' '}
            <button type="button" className="link-button" onClick={() => setSlug(slugify(name))}>
              {slugify(name)}
            </button>
          </p>
        ) : null}

        <TextAreaField
          label="Description"
          rows={3}
          disabled={!canEdit}
          value={description}
          hint="What this project is, in a sentence."
          error={fieldError('description')}
          onChange={(event) => setDescription(event.target.value)}
        />

        {canEdit ? (
          <div className="settings-panel__actions">
            <Button type="submit" loading={updateWorkspace.isPending} disabled={!dirty}>
              Save changes
            </Button>
            {dirty ? (
              <Button
                variant="ghost"
                onClick={() => {
                  setName(workspace.name)
                  setSlug(workspace.slug)
                  setDescription(workspace.description ?? '')
                  setError(null)
                }}
              >
                Discard
              </Button>
            ) : null}
          </div>
        ) : null}
      </form>

      <PublicationPanel workspace={workspace} />

      {/* Admin-only, like the rest of this screen: it decides where the workspace's
          documentation comes from, and the wrong repository could withdraw every
          page. The server enforces the same bar. */}
      <GitSyncPanel workspaceId={workspace.id} />

      {canDelete ? (
        <section className="danger-zone">
          <div>
            <h2 className="danger-zone__title">Delete this workspace</h2>
            <p className="danger-zone__detail">
              Removes every document, board, and task in {workspace.name}, for everyone on the team.
              This cannot be undone.
            </p>
          </div>
          <Button variant="danger" onClick={() => setConfirmOpen(true)}>
            Delete workspace
          </Button>
        </section>
      ) : null}

      <Modal
        title="Delete this workspace?"
        open={confirmOpen}
        onClose={() => {
          setConfirmOpen(false)
          setConfirmation('')
        }}
        width="sm"
        footer={
          <>
            <Button
              variant="ghost"
              onClick={() => {
                setConfirmOpen(false)
                setConfirmation('')
              }}
            >
              Keep it
            </Button>
            <Button
              variant="danger"
              onClick={handleDelete}
              loading={deleteWorkspace.isPending}
              disabled={confirmation !== workspace.name}
            >
              Delete workspace
            </Button>
          </>
        }
      >
        <p>
          Every document, board, and task in <strong>{workspace.name}</strong> will be removed for
          the whole team.
        </p>
        <TextField
          label={`Type "${workspace.name}" to confirm`}
          value={confirmation}
          autoComplete="off"
          onChange={(event) => setConfirmation(event.target.value)}
        />
      </Modal>
    </div>
  )
}
