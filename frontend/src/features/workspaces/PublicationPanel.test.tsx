import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import type { Publication, Workspace, WorkspaceRole } from '../../shared/types'
import { PublicationPanel } from './PublicationPanel'

function workspace(callerRole: WorkspaceRole): Workspace {
  return {
    id: 'workspace-1',
    name: 'Platform',
    description: null,
    slug: 'platform',
    callerRole,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

function state(overrides: Partial<Publication> = {}): Publication {
  return {
    published: false,
    publishedAt: null,
    publicPath: null,
    publicPages: 12,
    internalPages: 0,
    ...overrides,
  }
}

function renderPanel(callerRole: WorkspaceRole = 'OWNER') {
  return renderWithProviders(<PublicationPanel workspace={workspace(callerRole)} />)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('PublicationPanel', () => {
  it('reports a private workspace and what publishing would expose', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(state())

    renderPanel()

    expect(await screen.findByText('Private')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
  })

  /** The count is the whole point of the confirmation: it names the consequence. */
  it('states how many pages publishing exposes before doing it', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(state())
    const set = vi
      .spyOn(endpoints.publicationApi, 'set')
      .mockResolvedValue(state({ published: true, publicPath: '/docs/dana/platform' }))

    renderPanel()
    await userEvent.click(await screen.findByRole('button', { name: 'Publish documentation' }))

    const dialog = screen.getByRole('dialog', { name: 'Publish this documentation?' })
    // The count appears in both the sentence and the button label, so assert the
    // consequence text and the button separately rather than matching "12 pages".
    expect(within(dialog).getByText(/readable by anyone with the link/)).toBeInTheDocument()
    expect(within(dialog).getByText(/Boards, tasks, and your team list stay private/)).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Publish 12 pages' }))

    await waitFor(() => expect(set).toHaveBeenCalledWith('workspace-1', true))
  })

  it('shows the public address once published', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(
      state({ published: true, publishedAt: '2026-03-12T00:00:00Z', publicPath: '/docs/dana/platform' }),
    )

    renderPanel()

    expect(await screen.findByText('Published')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '/docs/dana/platform' })).toHaveAttribute(
      'href',
      '/docs/dana/platform',
    )
  })

  it('can make a published workspace private again', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(
      state({ published: true, publicPath: '/docs/dana/platform' }),
    )
    const set = vi.spyOn(endpoints.publicationApi, 'set').mockResolvedValue(state())

    renderPanel()
    await userEvent.click(await screen.findByRole('button', { name: 'Make private' }))

    await waitFor(() => expect(set).toHaveBeenCalledWith('workspace-1', false))
  })

  it('will not publish a workspace with nothing public to show', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(state({ publicPages: 0 }))

    renderPanel()

    expect(await screen.findByRole('button', { name: 'Publish documentation' })).toBeDisabled()
    expect(
      screen.getByText(/Write at least one page that is not marked internal/),
    ).toBeInTheDocument()
  })

  it('reports pages held back', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(
      state({ publicPages: 10, internalPages: 3 }),
    )

    renderPanel()

    expect(await screen.findByText(/3 pages are.*marked\s+internal/s)).toBeInTheDocument()
  })

  /** Matches the backend: changing publication requires ADMIN. */
  it('does not offer the control to a member', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(state())

    renderPanel('MEMBER')

    expect(await screen.findByText('Only admins and owners can change this.')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Publish documentation' }),
    ).not.toBeInTheDocument()
  })

  it('still tells a member that the workspace is published', async () => {
    vi.spyOn(endpoints.publicationApi, 'get').mockResolvedValue(
      state({ published: true, publicPath: '/docs/dana/platform' }),
    )

    renderPanel('MEMBER')

    // Anyone writing pages here needs to know they are writing in public.
    expect(await screen.findByText('Published')).toBeInTheDocument()
  })
})
