import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { Button } from '../../shared/components/Button'
import { TextAreaField, TextField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import { slugify } from '../../shared/utils/slugify'
import { useCreateWorkspace } from './useWorkspaces'

interface CreateWorkspaceDialogProps {
  open: boolean
  onClose: () => void
  onCreated: (workspaceId: string) => void
}

export function CreateWorkspaceDialog({ open, onClose, onCreated }: CreateWorkspaceDialogProps) {
  const createWorkspace = useCreateWorkspace()
  const { notify } = useToast()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [slug, setSlug] = useState('')
  /** Once the slug is hand-edited, stop overwriting it from the name. */
  const [slugTouched, setSlugTouched] = useState(false)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (open) {
      setName('')
      setDescription('')
      setSlug('')
      setSlugTouched(false)
      setError(null)
    }
  }, [open])

  const handleNameChange = (value: string) => {
    setName(value)
    if (!slugTouched) {
      setSlug(slugify(value))
    }
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      const workspace = await createWorkspace.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
        slug: slug.trim(),
      })
      notify(`Created ${workspace.name}`)
      onCreated(workspace.id)
    } catch (caught) {
      setError(caught)
    }
  }

  const fieldError = (field: string) =>
    error instanceof ApiError ? error.fieldError(field) : undefined

  return (
    <Modal
      title="New workspace"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="create-workspace" loading={createWorkspace.isPending}>
            Create workspace
          </Button>
        </>
      }
    >
      <form id="create-workspace" onSubmit={handleSubmit} noValidate>
        <div className="stack">
          {error && !(error instanceof ApiError && Object.keys(error.fieldErrors).length) ? (
            <p className="form-error" role="alert">
              {describeError(error, 'Could not create the workspace.')}
            </p>
          ) : null}

          <TextField
            label="Name"
            required
            autoFocus
            value={name}
            error={fieldError('name')}
            onChange={(event) => handleNameChange(event.target.value)}
          />

          <TextField
            label="URL slug"
            required
            value={slug}
            hint="Lowercase letters, numbers, and hyphens."
            error={fieldError('slug')}
            onChange={(event) => {
              setSlugTouched(true)
              setSlug(event.target.value)
            }}
          />

          <TextAreaField
            label="Description"
            rows={3}
            value={description}
            hint="What this project is, in a sentence."
            error={fieldError('description')}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>
      </form>
    </Modal>
  )
}
