import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { publicationApi } from '../../shared/api/endpoints'
import { ApiError } from '../../shared/api/client'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { SelectField, TextAreaField, TextField } from '../../shared/components/Field'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { describeError } from '../../shared/components/describeError'
import { Markdown } from '../../shared/components/Markdown'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import { DOCUMENT_TYPE_LABELS, roleAtLeast, type DocumentType } from '../../shared/types'
import { formatRelative } from '../../shared/utils/slugify'
import { useCurrentWorkspace } from '../workspaces/WorkspaceContext'
import { LinkDocumentDialog } from './LinkDocumentDialog'
import { ReferenceRail } from './ReferenceRail'
import {
  useDeleteDocument,
  useDocument,
  useDocumentReferences,
  useUpdateDocument,
} from './useDocuments'
import './DocumentDetailPage.css'

const DOCUMENT_TYPES = Object.keys(DOCUMENT_TYPE_LABELS) as DocumentType[]

export function DocumentDetailPage() {
  const workspace = useCurrentWorkspace()
  const { documentId = '' } = useParams()
  const navigate = useNavigate()
  const { notify, notifyError } = useToast()

  const { data: document, isPending, error, refetch } = useDocument(workspace.id, documentId)
  const { data: publication } = useQuery({
    queryKey: ['workspaces', workspace.id, 'publication'],
    queryFn: () => publicationApi.get(workspace.id),
  })
  const { data: references = [] } = useDocumentReferences(workspace.id, documentId)
  const updateDocument = useUpdateDocument(workspace.id, documentId)
  const deleteDocument = useDeleteDocument(workspace.id)

  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [slug, setSlug] = useState('')
  const [content, setContent] = useState('')
  const [documentType, setDocumentType] = useState<DocumentType>('GENERAL')
  const [internal, setInternal] = useState(false)
  const [saveError, setSaveError] = useState<unknown>(null)
  const [linkOpen, setLinkOpen] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const canWrite = roleAtLeast(workspace.callerRole, 'MEMBER')

  // Seed the editor whenever a freshly loaded document arrives.
  useEffect(() => {
    if (document) {
      setTitle(document.title)
      setSlug(document.slug)
      setContent(document.content)
      setDocumentType(document.documentType)
      setInternal(document.internal)
    }
  }, [document])

  if (isPending) {
    return <LoadingState label="Loading document" />
  }

  if (error || !document) {
    return (
      <div className="stack">
        <ErrorState
          title="Could not open this document"
          error={error ?? new Error('Document not found')}
          onRetry={refetch}
        />
        <p>
          <Link to={`/workspaces/${workspace.id}/documents`}>Back to documents</Link>
        </p>
      </div>
    )
  }

  const handleSave = async () => {
    setSaveError(null)
    try {
      await updateDocument.mutateAsync({
        title: title.trim(),
        slug: slug.trim(),
        content,
        documentType,
        internal,
      })
      notify('Document saved')
      setEditing(false)
    } catch (caught) {
      setSaveError(caught)
    }
  }

  const handleCancel = () => {
    setTitle(document.title)
    setSlug(document.slug)
    setContent(document.content)
    setDocumentType(document.documentType)
    setInternal(document.internal)
    setSaveError(null)
    setEditing(false)
  }

  const handleDelete = async () => {
    try {
      await deleteDocument.mutateAsync(documentId)
      notify(`Deleted ${document.title}`)
      navigate(`/workspaces/${workspace.id}/documents`)
    } catch (caught) {
      notifyError(describeError(caught, 'Could not delete the document.'))
    }
  }

  const fieldError = (field: string) =>
    saveError instanceof ApiError ? saveError.fieldError(field) : undefined

  return (
    <div className="stack">
      <nav className="crumbs" aria-label="Breadcrumb">
        <Link to={`/workspaces/${workspace.id}/documents`}>Documents</Link>
        <span aria-hidden="true">/</span>
        <span className="crumbs__current">{document.slug}</span>
      </nav>

      <div className="page-header">
        <div>
          <div className="row row--wrap">
            <Badge tone="trace">{DOCUMENT_TYPE_LABELS[document.documentType]}</Badge>
            <span className="doc-detail__slug">/{document.slug}</span>
            {/*
              Whether this page is readable by anyone is worth stating on the page
              itself, not only in workspace settings — the author needs to know
              what they are writing into.
            */}
            {document.internal ? (
              <Badge tone="neutral" title="Held back from the public site">
                Internal
              </Badge>
            ) : publication?.published ? (
              <Badge tone="success" title="Readable by anyone with the link">
                Public
              </Badge>
            ) : null}
          </div>
          <h1 className="page-header__title">{document.title}</h1>
          <p className="doc-detail__meta">Updated {formatRelative(document.updatedAt)}</p>
        </div>

        {canWrite ? (
          <div className="page-header__actions">
            {editing ? (
              <>
                <Button variant="ghost" onClick={handleCancel}>
                  Cancel
                </Button>
                <Button onClick={handleSave} loading={updateDocument.isPending}>
                  Save changes
                </Button>
              </>
            ) : (
              <>
                <Button variant="danger" size="sm" onClick={() => setConfirmDelete(true)}>
                  Delete
                </Button>
                <Button variant="secondary" onClick={() => setEditing(true)}>
                  Edit
                </Button>
              </>
            )}
          </div>
        ) : null}
      </div>

      <div className="doc-detail">
        <div className="doc-detail__main">
          {editing ? (
            <div className="stack">
              {saveError && !(saveError instanceof ApiError && Object.keys(saveError.fieldErrors).length) ? (
                <p className="form-error" role="alert">
                  {describeError(saveError, 'Could not save the document.')}
                </p>
              ) : null}

              <TextField
                label="Title"
                value={title}
                error={fieldError('title')}
                onChange={(event) => setTitle(event.target.value)}
              />

              <div className="doc-detail__row">
                <TextField
                  label="URL slug"
                  value={slug}
                  error={fieldError('slug')}
                  onChange={(event) => setSlug(event.target.value)}
                />
                <SelectField
                  label="Type"
                  value={documentType}
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

              <label className="visibility">
                <input
                  type="checkbox"
                  checked={internal}
                  onChange={(event) => setInternal(event.target.checked)}
                />
                <span className="visibility__body">
                  <span className="visibility__label">Keep this page internal</span>
                  <span className="visibility__hint">
                    {publication?.published
                      ? 'This workspace is published, so unchecked pages are readable by anyone with the link.'
                      : 'Internal pages stay private even if this workspace is published later.'}
                  </span>
                </span>
              </label>

              <TextAreaField
                label="Content (Markdown)"
                mono
                value={content}
                hint="Headings, lists, tables, and fenced code blocks are supported."
                error={fieldError('content')}
                onChange={(event) => setContent(event.target.value)}
              />

              <section className="doc-preview">
                <h2 className="mono-label">Preview</h2>
                <Markdown content={content} />
              </section>
            </div>
          ) : (
            <Markdown content={document.content} />
          )}
        </div>

        <ReferenceRail
          workspaceId={workspace.id}
          documentId={documentId}
          references={references}
          canWrite={canWrite}
          onAdd={() => setLinkOpen(true)}
        />
      </div>

      <LinkDocumentDialog
        workspaceId={workspace.id}
        documentId={documentId}
        excludedIds={references.filter((reference) => reference.outgoing).map((r) => r.relatedDocumentId)}
        open={linkOpen}
        onClose={() => setLinkOpen(false)}
      />

      <Modal
        title="Delete this document?"
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Keep it
            </Button>
            <Button variant="danger" onClick={handleDelete} loading={deleteDocument.isPending}>
              Delete document
            </Button>
          </>
        }
      >
        <p>
          <strong>{document.title}</strong> and its links to other documents and tasks will be
          removed. This cannot be undone.
        </p>
      </Modal>
    </div>
  )
}
