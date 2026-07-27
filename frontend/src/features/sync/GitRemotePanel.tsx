import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { syncApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useInstance } from '../../shared/instance/useInstance'
import './GitRemotePanel.css'

/** Bytes as something a person reads, for a number nobody needs to the byte. */
function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`
}

/**
 * This workspace as a git remote: clone it, push to it, work offline.
 *
 * Separate from the sync panel on purpose. That one follows a repository someone
 * else hosts; this one is the repository. Reading either as the other is the
 * mistake worth designing against, so they do not share a heading.
 */
export function GitRemotePanel({ workspaceId }: { workspaceId: string }) {
  const { instance } = useInstance()

  const repository = useQuery({
    queryKey: queryKeys.sync.repository(workspaceId),
    queryFn: () => syncApi.repository(workspaceId),
  })

  if (repository.isPending) {
    return <LoadingState label="Loading the repository" />
  }
  if (repository.error) {
    return (
      <ErrorState
        title="Could not load the repository"
        error={repository.error}
        onRetry={repository.refetch}
      />
    )
  }

  const current = repository.data
  if (!current?.enabled || !current.clonePath) {
    return null
  }

  // The origin comes from the browser: behind a reverse proxy or a tunnel the
  // server has no reliable idea what address anyone reached it on.
  const url = `${typeof window === 'undefined' ? '' : window.location.origin}${current.clonePath}`

  return (
    <section className="panel gitremote">
      <div className="panel__head">
        <h2 className="panel__title">Push and clone</h2>
        <p className="panel__note">
          {instance.name} hosts a git repository for this workspace. Clone it, edit the markdown in
          your own editor, and push — the pages here follow. Edits made in the interface become
          commits in the same repository, authored by whoever made them, so the two never drift
          apart.
        </p>
      </div>

      <label className="gitremote__url">
        <span className="mono-label">Remote URL</span>
        <input className="field__control" readOnly value={url} aria-label="Remote URL" />
      </label>

      <pre className="gitremote__commands">
        <code>
          {`git clone ${url}\n\n# or, on a repository you already have\ngit remote add devforge ${url}\ngit push devforge main`}
        </code>
      </pre>

      <p className="gitremote__hint">
        Sign in with a <Link to="/account">git access token</Link> as the password. Any username
        works — the token identifies its owner. Cloning needs the viewer role; pushing needs member.
      </p>

      <p className="gitremote__hint">
        {current.exists ? (
          <>
            The repository exists
            {current.sizeBytes != null ? ` and takes ${formatSize(current.sizeBytes)} on disk` : ''}.
            It lives on the server's disk rather than in the database, so a backup has to cover both.
          </>
        ) : (
          <>Nothing has been pushed yet. The repository appears the first time you clone or push.</>
        )}
      </p>
    </section>
  )
}
