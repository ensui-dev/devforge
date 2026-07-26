import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { documentApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { Button } from '../../shared/components/Button'
import { SelectField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import {
  DOCUMENT_TYPE_LABELS,
  REFERENCE_TYPE_LABELS,
  type ReferenceType,
} from '../../shared/types'
import { DOCUMENT_PICKER_SIZE, useAddReference } from './useDocuments'
import './LinkDocumentDialog.css'

interface LinkDocumentDialogProps {
  workspaceId: string
  documentId: string
  /** Documents already linked in this direction, so they are not offered twice. */
  excludedIds: string[]
  open: boolean
  onClose: () => void
}

const REFERENCE_TYPES = Object.keys(REFERENCE_TYPE_LABELS) as ReferenceType[]

export function LinkDocumentDialog({
  workspaceId,
  documentId,
  excludedIds,
  open,
  onClose,
}: LinkDocumentDialogProps) {
  const addReference = useAddReference(workspaceId, documentId)
  const { notify } = useToast()

  const [referenceType, setReferenceType] = useState<ReferenceType>('DEPENDS_ON')
  const [targetId, setTargetId] = useState('')
  const [query, setQuery] = useState('')
  const [error, setError] = useState<unknown>(null)

  // A single generous page is enough to pick from; the search box narrows it.
  const { data, isPending } = useQuery({
    queryKey: queryKeys.documents.list(workspaceId, 'ALL', 0, DOCUMENT_PICKER_SIZE),
    queryFn: () => documentApi.list(workspaceId, { size: DOCUMENT_PICKER_SIZE }),
    enabled: open,
  })

  useEffect(() => {
    if (open) {
      setReferenceType('DEPENDS_ON')
      setTargetId('')
      setQuery('')
      setError(null)
    }
  }, [open])

  const candidates = useMemo(() => {
    const excluded = new Set([...excludedIds, documentId])
    const term = query.trim().toLowerCase()
    return (data?.content ?? [])
      .filter((candidate) => !excluded.has(candidate.id))
      .filter(
        (candidate) =>
          !term ||
          candidate.title.toLowerCase().includes(term) ||
          candidate.slug.toLowerCase().includes(term),
      )
  }, [data, excludedIds, documentId, query])

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await addReference.mutateAsync({ targetDocumentId: targetId, referenceType })
      notify('Documents linked')
      onClose()
    } catch (caught) {
      setError(caught)
    }
  }

  return (
    <Modal
      title="Link a document"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="link-document"
            loading={addReference.isPending}
            disabled={!targetId}
          >
            Add link
          </Button>
        </>
      }
    >
      <form id="link-document" onSubmit={handleSubmit} noValidate>
        <div className="stack">
          {error ? (
            <p className="form-error" role="alert">
              {describeError(error, 'Could not link the documents.')}
            </p>
          ) : null}

          <SelectField
            label="Relationship"
            value={referenceType}
            hint="Read as: this document → the one you pick."
            onChange={(event) => setReferenceType(event.target.value as ReferenceType)}
          >
            {REFERENCE_TYPES.map((type) => (
              <option key={type} value={type}>
                {REFERENCE_TYPE_LABELS[type]}
              </option>
            ))}
          </SelectField>

          <div className="field">
            <label className="field__label mono-label" htmlFor="link-target-search">
              Target document
            </label>
            <input
              id="link-target-search"
              className="field__control"
              type="search"
              placeholder="Filter by title or slug…"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>

          {isPending ? (
            <p className="link-picker__status">Loading documents…</p>
          ) : candidates.length === 0 ? (
            <p className="link-picker__status">
              {query
                ? 'No documents match that filter.'
                : 'Every other document in this workspace is already linked.'}
            </p>
          ) : (
            <ul className="link-picker" role="radiogroup" aria-label="Target document">
              {candidates.map((candidate) => (
                <li key={candidate.id}>
                  <label
                    className={
                      candidate.id === targetId
                        ? 'link-option link-option--selected'
                        : 'link-option'
                    }
                  >
                    <input
                      type="radio"
                      name="target-document"
                      value={candidate.id}
                      checked={candidate.id === targetId}
                      onChange={() => setTargetId(candidate.id)}
                    />
                    <span className="link-option__body">
                      <span className="link-option__title">{candidate.title}</span>
                      <span className="link-option__meta">
                        {DOCUMENT_TYPE_LABELS[candidate.documentType]} · /{candidate.slug}
                      </span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          )}
        </div>
      </form>
    </Modal>
  )
}
