import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/client'
import { Button } from '../../shared/components/Button'
import { SelectField, TextField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import { DOCUMENT_TYPE_LABELS, type DocumentType } from '../../shared/types'
import { slugify } from '../../shared/utils/slugify'
import { useCreateDocument } from './useDocuments'

interface CreateDocumentDialogProps {
  workspaceId: string
  open: boolean
  onClose: () => void
  onCreated: (documentId: string) => void
}

const DOCUMENT_TYPES = Object.keys(DOCUMENT_TYPE_LABELS) as DocumentType[]

export function CreateDocumentDialog({
  workspaceId,
  open,
  onClose,
  onCreated,
}: CreateDocumentDialogProps) {
  const createDocument = useCreateDocument(workspaceId)
  const { notify } = useToast()

  const [title, setTitle] = useState('')
  const [slug, setSlug] = useState('')
  const [slugTouched, setSlugTouched] = useState(false)
  const [documentType, setDocumentType] = useState<DocumentType>('ARCHITECTURE')
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (open) {
      setTitle('')
      setSlug('')
      setSlugTouched(false)
      setDocumentType('ARCHITECTURE')
      setError(null)
    }
  }, [open])

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      const document = await createDocument.mutateAsync({
        title: title.trim(),
        slug: slug.trim(),
        content: '',
        documentType,
      })
      notify(`Created ${document.title}`)
      onCreated(document.id)
    } catch (caught) {
      setError(caught)
    }
  }

  const fieldError = (field: string) =>
    error instanceof ApiError ? error.fieldError(field) : undefined

  return (
    <Modal
      title="New document"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="create-document" loading={createDocument.isPending}>
            Create document
          </Button>
        </>
      }
    >
      <form id="create-document" onSubmit={handleSubmit} noValidate>
        <div className="stack">
          {error && !(error instanceof ApiError && Object.keys(error.fieldErrors).length) ? (
            <p className="form-error" role="alert">
              {describeError(error, 'Could not create the document.')}
            </p>
          ) : null}

          <TextField
            label="Title"
            required
            autoFocus
            value={title}
            error={fieldError('title')}
            onChange={(event) => {
              setTitle(event.target.value)
              if (!slugTouched) {
                setSlug(slugify(event.target.value))
              }
            }}
          />

          <TextField
            label="URL slug"
            required
            value={slug}
            hint="Used in links to this document."
            error={fieldError('slug')}
            onChange={(event) => {
              setSlugTouched(true)
              setSlug(event.target.value)
            }}
          />

          <SelectField
            label="Type"
            value={documentType}
            hint="Sets how this page is filtered and labelled."
            error={fieldError('documentType')}
            onChange={(event) => setDocumentType(event.target.value as DocumentType)}
          >
            {DOCUMENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {DOCUMENT_TYPE_LABELS[type]}
              </option>
            ))}
          </SelectField>
        </div>
      </form>
    </Modal>
  )
}
