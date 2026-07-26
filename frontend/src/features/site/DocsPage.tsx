import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router-dom'
import { handbookApi } from '../../shared/api/endpoints'
import { Badge } from '../../shared/components/Badge'
import { EmptyState } from '../../shared/components/EmptyState'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { Markdown } from '../../shared/components/Markdown'
import {
  DOCUMENT_TYPE_LABELS,
  REFERENCE_TYPE_INVERSE_LABELS,
  REFERENCE_TYPE_LABELS,
  type DocumentReference,
  type HandbookEntry,
} from '../../shared/types'
import { formatRelative } from '../../shared/utils/slugify'
import { useInstance } from '../../shared/instance/useInstance'
import { SiteChrome } from './SiteChrome'
import './DocsPage.css'

/**
 * Groups pages for the sidebar. A slug that is not listed still appears, under
 * "More", so a page added in DevForge shows up without a code change.
 */
const GROUPS: { heading: string; slugs: string[] }[] = [
  { heading: 'Start here', slugs: ['welcome', 'quickstart'] },
  { heading: 'Concepts', slugs: ['reference-graph', 'document-types', 'roles-and-permissions'] },
  {
    heading: 'Tutorials',
    slugs: [
      'tutorial-writing-documents',
      'tutorial-linking',
      'tutorial-boards',
      'tutorial-linking-tasks',
      'tutorial-search',
      'publishing',
    ],
  },
  {
    heading: 'Use cases',
    slugs: ['use-case-onboarding', 'use-case-decisions', 'use-case-runbooks'],
  },
  {
    heading: 'API reference',
    slugs: [
      'api-authentication',
      'api-workspaces',
      'api-documents',
      'api-boards',
      'api-public-docs',
      'api-errors',
    ],
  },
  { heading: 'Operations', slugs: ['running-locally', 'troubleshooting', 'tech-stack'] },
]

function groupEntries(entries: HandbookEntry[]) {
  const bySlug = new Map(entries.map((entry) => [entry.slug, entry]))
  const placed = new Set<string>()

  const groups = GROUPS.map(({ heading, slugs }) => {
    const members = slugs.flatMap((slug) => {
      const entry = bySlug.get(slug)
      if (entry) {
        placed.add(slug)
        return [entry]
      }
      return []
    })
    return { heading, members }
  }).filter((group) => group.members.length > 0)

  const rest = entries.filter((entry) => !placed.has(entry.slug))
  return rest.length > 0 ? [...groups, { heading: 'More', members: rest }] : groups
}

/** One typed edge, phrased from the side of the document being read. */
function EdgeList({
  references,
  outgoing,
  docsBase,
}: {
  references: DocumentReference[]
  outgoing: boolean
  /** `/docs/{handle}/{workspace}` — the prefix every page link shares. */
  docsBase: string
}) {
  const labels = outgoing ? REFERENCE_TYPE_LABELS : REFERENCE_TYPE_INVERSE_LABELS

  return (
    <ul className="edges">
      {references.map((reference) => (
        <li className="edge" key={reference.id}>
          <span className="edge__type">{labels[reference.referenceType]}</span>
          {reference.relatedDocumentSlug ? (
            <Link className="edge__target" to={`${docsBase}/${reference.relatedDocumentSlug}`}>
              {reference.relatedDocumentTitle ?? 'Untitled document'}
            </Link>
          ) : (
            <span className="edge__target">{reference.relatedDocumentTitle ?? 'Untitled'}</span>
          )}
        </li>
      ))}
    </ul>
  )
}

export function DocsPage() {
  const { handle: handleParam, workspaceSlug: workspaceParam, slug } = useParams()

  // Which documentation this instance opens when the URL names none.
  const { instance, isLoading: instanceLoading } = useInstance()

  const [defaultHandle = '', defaultWorkspace = ''] = (instance.handbookPath ?? '').split('/')
  const handle = handleParam ?? defaultHandle
  // A URL with a handle but no workspace is an owner page, not a workspace.
  const activeWorkspace = handleParam ? (workspaceParam ?? '') : defaultWorkspace
  const showingOwner = Boolean(handleParam) && !workspaceParam

  const contents = useQuery({
    queryKey: ['handbook', 'contents', handle, activeWorkspace],
    queryFn: () => handbookApi.contents(handle, activeWorkspace),
    enabled: handle.length > 0 && activeWorkspace.length > 0,
  })

  // Drives both the owner page and the fallback directory.
  const owner = useQuery({
    queryKey: ['handbook', 'owner', handle],
    queryFn: () => handbookApi.byOwner(handle),
    enabled: showingOwner,
  })

  const directory = useQuery({
    queryKey: ['handbook', 'directory'],
    queryFn: handbookApi.directory,
    enabled: (!showingOwner && handle.length === 0) || Boolean(contents.error),
  })

  // The first page in the first group is the landing page for /docs.
  const groups = useMemo(
    () => groupEntries(contents.data?.entries ?? []),
    [contents.data],
  )
  const firstSlug = groups[0]?.members[0]?.slug

  const page = useQuery({
    queryKey: ['handbook', 'page', handle, activeWorkspace, slug],
    queryFn: () => handbookApi.page(handle, activeWorkspace, slug as string),
    enabled: Boolean(slug) && activeWorkspace.length > 0,
  })

  // A legacy /docs/{slug} link: send it to where that documentation now lives.
  if (showingOwner && owner.data?.movedTo) {
    return <Navigate to={owner.data.movedTo} replace />
  }

  if (
    instanceLoading ||
    (showingOwner && owner.isPending) ||
    (!showingOwner && activeWorkspace.length > 0 && contents.isPending)
  ) {
    return (
      <SiteChrome>
        <div className="docs-shell docs-shell--plain">
          <LoadingState label="Loading the handbook" />
        </div>
      </SiteChrome>
    )
  }

  // An owner page, or nothing specific to show: list what is available.
  if (showingOwner || activeWorkspace.length === 0 || contents.error || !contents.data) {
    const published = showingOwner ? (owner.data?.workspaces ?? []) : (directory.data ?? [])
    return (
      <SiteChrome>
        <div className="docs-shell docs-shell--plain">
          <div className="stack">
            {contents.error && workspaceParam ? (
              <ErrorState
                title="That documentation is not available"
                error={contents.error}
                onRetry={contents.refetch}
              />
            ) : null}

            {published.length > 0 ? (
              <>
                <h1 className="docs-directory__title">
                  {showingOwner ? `Documentation by ${handle}` : 'Published documentation'}
                </h1>
                <ul className="docs-directory">
                  {published.map((entry) => (
                    <li key={entry.slug}>
                      <Link className="docs-directory__item" to={entry.publicPath}>
                        <span className="docs-directory__name">{entry.name}</span>
                        {entry.description ? (
                          <span className="docs-directory__desc">{entry.description}</span>
                        ) : null}
                        <span className="docs-directory__meta">
                          {entry.pageCount} {entry.pageCount === 1 ? 'page' : 'pages'}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <EmptyState
                title={
                  showingOwner
                    ? `${handle} has not published anything`
                    : 'No published documentation yet'
                }
                description="A workspace admin can publish theirs from the workspace's Settings tab, and it appears here."
              />
            )}
          </div>
        </div>
      </SiteChrome>
    )
  }

  if (!slug && firstSlug) {
    return <Navigate to={`/docs/${contents.data.ownerHandle}/${contents.data.slug}/${firstSlug}`} replace />
  }

  return (
    <SiteChrome>
      <div className="docs-shell">
        <nav className="docs-rail" aria-label="Handbook contents">
          <p className="mono-label docs-rail__title">{contents.data.name}</p>
          {groups.map((group) => (
            <div className="docs-rail__group" key={group.heading}>
              <p className="docs-rail__heading">{group.heading}</p>
              {group.members.map((entry) => (
                <Link
                  key={entry.slug}
                  to={`/docs/${contents.data.ownerHandle}/${contents.data.slug}/${entry.slug}`}
                  className={
                    entry.slug === slug ? 'docs-rail__link is-active' : 'docs-rail__link'
                  }
                  aria-current={entry.slug === slug ? 'page' : undefined}
                >
                  {entry.title}
                </Link>
              ))}
            </div>
          ))}
        </nav>

        <article className="docs-body">
          {page.isPending ? <LoadingState label="Loading page" /> : null}

          {page.error ? (
            <div className="stack">
              <ErrorState
                title="Could not open this page"
                error={page.error}
                onRetry={page.refetch}
              />
              <p>
                <Link to={`/docs/${contents.data.ownerHandle}/${contents.data.slug}`}>Back to the contents</Link>
              </p>
            </div>
          ) : null}

          {page.data ? (
            <>
              <header className="docs-head">
                <div className="row">
                  <Badge tone="trace">{DOCUMENT_TYPE_LABELS[page.data.documentType]}</Badge>
                  <span className="docs-head__slug">/{page.data.slug}</span>
                </div>
                <p className="docs-head__meta">Updated {formatRelative(page.data.updatedAt)}</p>
              </header>

              <Markdown content={page.data.content} />

              {page.data.references.length > 0 ? (
                <aside className="connections" aria-label="Connections">
                  <h2 className="connections__title">Connections</h2>
                  <div className="connections__groups">
                    {page.data.references.some((r) => r.outgoing) ? (
                      <div className="connections__group">
                        <p className="mono-label">This page</p>
                        <EdgeList
                          references={page.data.references.filter((r) => r.outgoing)}
                          outgoing
                          docsBase={`/docs/${contents.data.ownerHandle}/${contents.data.slug}`}
                        />
                      </div>
                    ) : null}
                    {page.data.references.some((r) => !r.outgoing) ? (
                      <div className="connections__group">
                        <p className="mono-label">Referenced by</p>
                        <EdgeList
                          references={page.data.references.filter((r) => !r.outgoing)}
                          outgoing={false}
                          docsBase={`/docs/${contents.data.ownerHandle}/${contents.data.slug}`}
                        />
                      </div>
                    ) : null}
                  </div>
                </aside>
              ) : null}
            </>
          ) : null}
        </article>
      </div>
    </SiteChrome>
  )
}
