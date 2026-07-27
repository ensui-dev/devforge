import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../shared/components/Button'
import { useToast } from '../../shared/components/useToast'
import { describeError } from '../../shared/components/describeError'
import {
  DOCUMENT_TYPE_LABELS,
  REFERENCE_TYPE_INVERSE_LABELS,
  REFERENCE_TYPE_LABELS,
  type DocumentReference,
} from '../../shared/types'
import { formatRelative } from '../../shared/utils/slugify'
import { ReferenceChangesDialog } from './ReferenceChangesDialog'
import { useRemoveReference } from './useDocuments'
import './ReferenceRail.css'

interface ReferenceRailProps {
  workspaceId: string
  documentId: string
  references: DocumentReference[]
  canWrite: boolean
  onAdd: () => void
}

/**
 * The connections panel: every typed edge touching this document, rendered as a
 * plotted trace.
 *
 * Outgoing links and backlinks are separated because they answer different
 * questions — "what does this rely on" versus "what breaks if I change this" —
 * and the second is the one that is normally invisible in a wiki.
 */
export function ReferenceRail({
  workspaceId,
  documentId,
  references,
  canWrite,
  onAdd,
}: ReferenceRailProps) {
  const removeReference = useRemoveReference(workspaceId, documentId)
  const { notify, notifyError } = useToast()
  const [removingId, setRemovingId] = useState<string | null>(null)
  const [changesFor, setChangesFor] = useState<DocumentReference | null>(null)

  const outgoing = references.filter((reference) => reference.outgoing)
  const incoming = references.filter((reference) => !reference.outgoing)

  const handleRemove = async (reference: DocumentReference) => {
    setRemovingId(reference.id)
    try {
      await removeReference.mutateAsync({
        referenceId: reference.id,
        relatedDocumentId: reference.relatedDocumentId,
      })
      notify('Reference removed')
    } catch (error) {
      notifyError(describeError(error, 'Could not remove the reference.'))
    } finally {
      setRemovingId(null)
    }
  }

  const renderGroup = (
    heading: string,
    items: DocumentReference[],
    outgoingGroup: boolean,
    emptyNote: string,
  ) => (
    <section className="rail-group">
      <h3 className="rail-group__heading mono-label">{heading}</h3>
      {items.length === 0 ? (
        <p className="rail-group__empty">{emptyNote}</p>
      ) : (
        <ul className="rail-group__list">
          {items.map((reference) => (
            <li key={reference.id} className="edge">
              <span className="edge__type">
                {outgoingGroup
                  ? REFERENCE_TYPE_LABELS[reference.referenceType]
                  : REFERENCE_TYPE_INVERSE_LABELS[reference.referenceType]}
              </span>
              <div className="edge__body">
                <Link
                  className="edge__target"
                  to={`/workspaces/${workspaceId}/documents/${reference.relatedDocumentId}`}
                >
                  {reference.relatedDocumentTitle ?? 'Untitled document'}
                </Link>
                {reference.relatedDocumentType ? (
                  <span className="edge__target-type">
                    {DOCUMENT_TYPE_LABELS[reference.relatedDocumentType]}
                  </span>
                ) : null}
                {/* The point of the marker is that it is a way in, not a badge:
                    "something moved" is only useful next to "here is what". */}
                {reference.behind ? (
                  <button
                    type="button"
                    className="edge__behind"
                    onClick={() => setChangesFor(reference)}
                  >
                    {outgoingGroup ? 'Changed since this page' : 'Not updated since this page'}
                    {reference.relatedChangedAt ? (
                      <span className="edge__behind-when">
                        {' · '}
                        {formatRelative(reference.relatedChangedAt)}
                      </span>
                    ) : null}
                  </button>
                ) : null}
              </div>
              {/* Only the declaring document can remove an edge, so backlinks show
                  no control — matching what the API allows. */}
              {canWrite && outgoingGroup ? (
                <button
                  type="button"
                  className="edge__remove"
                  onClick={() => handleRemove(reference)}
                  disabled={removingId === reference.id}
                  aria-label={`Remove reference to ${reference.relatedDocumentTitle ?? 'document'}`}
                >
                  &times;
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  )

  return (
    <aside className="reference-rail" aria-label="Connections">
      <div className="reference-rail__head">
        <h2 className="reference-rail__title">Connections</h2>
        {canWrite ? (
          <Button variant="secondary" size="sm" onClick={onAdd}>
            Link document
          </Button>
        ) : null}
      </div>

      {renderGroup('This document', outgoing, true, 'No outgoing links yet.')}
      {renderGroup('Referenced by', incoming, false, 'Nothing points here yet.')}

      {/* Mounted only while open, so each opening fetches what is current rather
          than showing what was true when the page loaded. */}
      {changesFor ? (
        <ReferenceChangesDialog
          workspaceId={workspaceId}
          documentId={documentId}
          reference={changesFor}
          onClose={() => setChangesFor(null)}
        />
      ) : null}
    </aside>
  )
}
