import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import type { GitRepository } from '../../shared/types'
import { GitRemotePanel } from './GitRemotePanel'

function repository(overrides: Partial<GitRepository> = {}): GitRepository {
  return {
    enabled: true,
    exists: true,
    clonePath: '/git/ada/platform.git',
    sizeBytes: 2_400_000,
    ...overrides,
  }
}

function render() {
  return renderWithProviders(<GitRemotePanel workspaceId="w1" />, { withAuth: true })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('GitRemotePanel', () => {
  it('shows an address that can be pasted into git', async () => {
    vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue(repository())

    render()

    // Absolute, and built from the origin the browser actually reached — a server
    // behind a proxy or a tunnel cannot know that address.
    expect(await screen.findByLabelText('Remote URL')).toHaveValue(
      `${window.location.origin}/git/ada/platform.git`,
    )
  })

  it('spells out both ways to use it', async () => {
    vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue(repository())

    render()

    const commands = (await screen.findByText(/git clone/)).textContent ?? ''
    expect(commands).toContain('git clone')
    expect(commands).toContain('git remote add devforge')
    expect(commands).toContain('git push devforge main')
  })

  it('says what to sign in with, and links to where tokens are made', async () => {
    vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue(repository())

    render()

    expect(await screen.findByRole('link', { name: 'git access token' }))
      .toHaveAttribute('href', '/account')
  })

  it('says the repository is not there yet rather than showing a size', async () => {
    vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue(
      repository({ exists: false, sizeBytes: null }),
    )

    render()

    expect(await screen.findByText(/Nothing has been pushed yet/)).toBeInTheDocument()
  })

  it('reports the size in units a person reads', async () => {
    vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue(repository())

    render()

    expect(await screen.findByText(/2\.3 MB on disk/)).toBeInTheDocument()
  })

  /** An instance that only syncs from elsewhere has nothing to offer here. */
  it('shows nothing at all when the instance does not serve git', async () => {
    vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue(
      repository({ enabled: false, clonePath: null, exists: false, sizeBytes: null }),
    )

    const { container } = render()

    await vi.waitFor(() => expect(container.textContent).not.toContain('Loading'))
    expect(screen.queryByText('Push and clone')).not.toBeInTheDocument()
  })
})
