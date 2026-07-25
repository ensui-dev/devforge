import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { WorkspaceListPage } from './WorkspaceListPage'
import * as endpoints from '../../shared/api/endpoints'
import type { Workspace } from '../../shared/types'

function workspace(overrides: Partial<Workspace> = {}): Workspace {
  return {
    id: 'workspace-1',
    name: 'Platform',
    description: 'Core services',
    slug: 'platform',
    callerRole: 'OWNER',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

/**
 * The API layer is mocked rather than `fetch`, so these tests exercise the screen's
 * behaviour — loading, empty, error, and creation — without asserting on URLs.
 */
describe('WorkspaceListPage', () => {
  beforeEach(() => {
    window.localStorage.setItem(
      'devforge.session',
      JSON.stringify({
        accessToken: 'token',
        expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
        user: { id: 'user-1', email: 'dev@example.com', displayName: 'Dev Example' },
      }),
    )
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('lists the workspaces the user belongs to', async () => {
    vi.spyOn(endpoints.workspaceApi, 'list').mockResolvedValue([
      workspace(),
      workspace({ id: 'workspace-2', name: 'Mobile', slug: 'mobile', callerRole: 'VIEWER' }),
    ])

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    expect(await screen.findByRole('heading', { name: 'Platform' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Mobile' })).toBeInTheDocument()
  })

  it('shows the role held in each workspace', async () => {
    vi.spyOn(endpoints.workspaceApi, 'list').mockResolvedValue([
      workspace({ callerRole: 'VIEWER' }),
    ])

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    expect(await screen.findByText('VIEWER')).toBeInTheDocument()
  })

  it('links each card to the workspace', async () => {
    vi.spyOn(endpoints.workspaceApi, 'list').mockResolvedValue([workspace()])

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    await waitFor(() =>
      expect(screen.getByRole('link', { name: /Platform/ })).toHaveAttribute(
        'href',
        '/workspaces/workspace-1',
      ),
    )
  })

  it('invites the user to create one when there are none', async () => {
    vi.spyOn(endpoints.workspaceApi, 'list').mockResolvedValue([])

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    expect(await screen.findByText('No workspaces yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create workspace' })).toBeInTheDocument()
  })

  it('reports a failure with the backend’s message', async () => {
    const { ApiError } = await import('../../shared/api/client')
    vi.spyOn(endpoints.workspaceApi, 'list').mockRejectedValue(
      new ApiError('Service unavailable', 503),
    )

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable')
  })

  it('creates a workspace from the dialog and derives the slug from the name', async () => {
    vi.spyOn(endpoints.workspaceApi, 'list').mockResolvedValue([])
    const create = vi
      .spyOn(endpoints.workspaceApi, 'create')
      .mockResolvedValue(workspace({ name: 'New Platform', slug: 'new-platform' }))

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    await userEvent.click(await screen.findByRole('button', { name: 'Create workspace' }))
    await userEvent.type(screen.getByLabelText('Name'), 'New Platform')

    expect(screen.getByLabelText('URL slug')).toHaveValue('new-platform')

    // The empty state's trigger and the dialog's submit share a label, so the
    // query is scoped to the dialog.
    const dialog = within(screen.getByRole('dialog', { name: 'New workspace' }))
    await userEvent.click(dialog.getByRole('button', { name: 'Create workspace' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'New Platform', slug: 'new-platform' }),
      ),
    )
  })

  it('keeps a hand-edited slug when the name changes afterwards', async () => {
    vi.spyOn(endpoints.workspaceApi, 'list').mockResolvedValue([])

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    await userEvent.click(await screen.findByRole('button', { name: 'Create workspace' }))
    await userEvent.type(screen.getByLabelText('Name'), 'First')
    await userEvent.clear(screen.getByLabelText('URL slug'))
    await userEvent.type(screen.getByLabelText('URL slug'), 'custom-slug')
    await userEvent.type(screen.getByLabelText('Name'), ' Changed')

    expect(screen.getByLabelText('URL slug')).toHaveValue('custom-slug')
  })

  it('surfaces a field error returned by the backend', async () => {
    vi.spyOn(endpoints.workspaceApi, 'list').mockResolvedValue([])
    const { ApiError } = await import('../../shared/api/client')
    vi.spyOn(endpoints.workspaceApi, 'create').mockRejectedValue(
      new ApiError('Validation failed', 400, { slug: 'must be lowercase alphanumeric with hyphens' }),
    )

    renderWithProviders(<WorkspaceListPage />, { withAuth: true })

    await userEvent.click(await screen.findByRole('button', { name: 'Create workspace' }))
    await userEvent.type(screen.getByLabelText('Name'), 'Bad One')

    const dialog = within(screen.getByRole('dialog', { name: 'New workspace' }))
    await userEvent.click(dialog.getByRole('button', { name: 'Create workspace' }))

    expect(
      await screen.findByText('must be lowercase alphanumeric with hyphens'),
    ).toBeInTheDocument()
  })
})
