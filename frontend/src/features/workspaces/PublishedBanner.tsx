import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { publicationApi } from '../../shared/api/endpoints'
import type { Workspace } from '../../shared/types'
import './PublishedBanner.css'

/**
 * States that this workspace's documentation is public, on every screen inside it.
 *
 * Publishing is opt-out per page, which means a page written later is public the
 * moment it is saved. That is only safe if nobody can forget the workspace is
 * published — so this sits in the navigation rail rather than in settings.
 */
export function PublishedBanner({ workspace }: { workspace: Workspace }) {
  const { data } = useQuery({
    queryKey: ['workspaces', workspace.id, 'publication'],
    queryFn: () => publicationApi.get(workspace.id),
  })

  if (!data?.published || !data.publicPath) {
    return null
  }

  return (
    <aside className="published-banner">
      <p className="published-banner__title">Documentation is public</p>
      <p className="published-banner__detail">
        {data.publicPages} {data.publicPages === 1 ? 'page is' : 'pages are'} readable by anyone
        with the link.
      </p>
      <Link className="published-banner__link" to={data.publicPath}>
        View public site
      </Link>
    </aside>
  )
}
