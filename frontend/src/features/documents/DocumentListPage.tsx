import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { EmptyState } from '../../shared/components/EmptyState'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import {
  DOCUMENT_TYPE_LABELS,
  roleAtLeast,
  type DocumentType,
} from '../../shared/types'
import { formatRelative } from '../../shared/utils/slugify'
import { useCurrentWorkspace } from '../workspaces/WorkspaceContext'
import { CreateDocumentDialog } from './CreateDocumentDialog'
import { useDocumentList } from './useDocuments'
import './DocumentListPage.css'

const TYPE_FILTERS: (DocumentType | 'ALL')[] = [
  'ALL',
  'ARCHITECTURE',
  'DECISION',
  'API',
  'CODE',
  'PROCEDURE',
  'RUNBOOK',
  'TECHNOLOGY',
  'TECH_STACK',
  'GENERAL',
]

export function DocumentListPage() {
  const workspace = useCurrentWorkspace()
  const navigate = useNavigate()

  const [documentType, setDocumentType] = useState<DocumentType | 'ALL'>('ALL')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data, isPending, error, refetch, isPlaceholderData } = useDocumentList(workspace.id, {
    documentType,
    page,
    search,
  })

  const canWrite = roleAtLeast(workspace.callerRole, 'MEMBER')
  const searching = search.trim().length > 0
  const filtered = !searching && documentType !== 'ALL'

  /*
   * Self-corrects a page that no longer exists — after a delete, or a filter that
   * shortens the list. Without this the page renders empty *and* the pager
   * disappears with it (it lives inside the has-content branch), leaving no way
   * back to the results.
   */
  useEffect(() => {
    if (data && data.totalPages > 0 && page >= data.totalPages) {
      setPage(data.totalPages - 1)
    }
  }, [data, page])

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <p className="mono-label">Documents</p>
          <h1 className="page-header__title">Knowledge base</h1>
          <p className="page-header__subtitle">
            Architecture, decisions, procedures, and the stack — linked to each other and to the work
            that changes them.
          </p>
        </div>
        {canWrite ? (
          <div className="page-header__actions">
            <Button onClick={() => setDialogOpen(true)}>New document</Button>
          </div>
        ) : null}
      </div>

      <div className="doc-controls">
        <label className="doc-search">
          <span className="visually-hidden">Search documents</span>
          <input
            type="search"
            className="doc-search__input"
            placeholder="Search titles and content…"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </label>

        <div className="doc-filters" role="group" aria-label="Filter by type">
          {TYPE_FILTERS.map((type) => (
            <button
              key={type}
              type="button"
              className={
                type === documentType && !searching
                  ? 'doc-filter doc-filter--active'
                  : 'doc-filter'
              }
              onClick={() => {
                setDocumentType(type)
                setPage(0)
              }}
              disabled={searching}
              title={searching ? 'Clear the search to filter by type' : undefined}
            >
              {type === 'ALL' ? 'All' : DOCUMENT_TYPE_LABELS[type]}
            </button>
          ))}
        </div>
      </div>

      {isPending ? <LoadingState label="Loading documents" /> : null}
      {error ? <ErrorState error={error} onRetry={refetch} /> : null}

      {/*
        An empty result has three different causes, and saying "no documents yet"
        for all of them tells the reader something untrue — a filter matching
        nothing is not an empty workspace. Each case names its cause and offers
        the way out of it.
      */}
      {data && data.content.length === 0 ? (
        searching ? (
          <EmptyState
            title="No documents match that search"
            description="Try fewer words, or a term that would appear in the document body."
            action={
              <Button variant="secondary" onClick={() => setSearch('')}>
                Clear search
              </Button>
            }
          />
        ) : filtered ? (
          <EmptyState
            title={`No ${DOCUMENT_TYPE_LABELS[documentType as DocumentType].toLowerCase()} documents`}
            description="This workspace has documents of other types. Clear the filter to see them all."
            action={
              <Button variant="secondary" onClick={() => setDocumentType('ALL')}>
                Show all documents
              </Button>
            }
          />
        ) : (
          <EmptyState
            title="No documents yet"
            description="Start with the architecture overview or a decision record — later pages can point at it."
            action={
              canWrite ? <Button onClick={() => setDialogOpen(true)}>New document</Button> : undefined
            }
          />
        )
      ) : null}

      {data && data.content.length > 0 ? (
        <>
          <p className="doc-count mono-label">
            {searching
              ? `${data.totalElements} matching ${data.totalElements === 1 ? 'document' : 'documents'}`
              : `${data.totalElements} ${data.totalElements === 1 ? 'document' : 'documents'}`}
          </p>

          <ul className={isPlaceholderData ? 'doc-list doc-list--stale' : 'doc-list'}>
            {data.content.map((document) => (
              <li key={document.id}>
                <Link className="doc-row" to={`/workspaces/${workspace.id}/documents/${document.id}`}>
                  <div className="doc-row__head">
                    <Badge tone="trace">{DOCUMENT_TYPE_LABELS[document.documentType]}</Badge>
                    <span className="doc-row__slug">/{document.slug}</span>
                    {document.internal ? <Badge tone="neutral">Internal</Badge> : null}
                  </div>
                  <h2 className="doc-row__title">{document.title}</h2>
                  {document.excerpt ? <p className="doc-row__excerpt">{document.excerpt}</p> : null}
                  <p className="doc-row__meta">Updated {formatRelative(document.updatedAt)}</p>
                </Link>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 ? (
            <nav className="pager" aria-label="Pagination">
              <Button
                variant="secondary"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Previous
              </Button>
              <span className="pager__status mono-label">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={data.last}
                onClick={() => setPage((current) => current + 1)}
              >
                Next
              </Button>
            </nav>
          ) : null}
        </>
      ) : null}

      <CreateDocumentDialog
        workspaceId={workspace.id}
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onCreated={(documentId) => {
          setDialogOpen(false)
          navigate(`/workspaces/${workspace.id}/documents/${documentId}`)
        }}
      />
    </div>
  )
}
