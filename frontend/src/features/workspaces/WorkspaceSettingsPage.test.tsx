import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import { ApiError } from '../../shared/api/client'
import type { SyncSettings, Workspace, WorkspaceRole } from '../../shared/types'
import { WorkspaceContext } from './WorkspaceContext'
import { WorkspaceSettingsPage } from './WorkspaceSettingsPage'

function workspace(callerRole: WorkspaceRole = 'OWNER'): Workspace {
  return {
    id: 'workspace-1',
    name: 'Platform',
    description: 'Core services',
    slug: 'platform',
    callerRole,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

const unconfiguredSync: SyncSettings = {
  configured: false,
  repositoryUrl: '',
  branch: 'main',
  documentPath: '',
  defaultType: 'GENERAL',
  deletionPolicy: 'ARCHIVE',
  enabled: false,
  hasAccessToken: false,
  hasWebhookSecret: false,
  webhookUrl: null,
  webhookId: null,
  lastAttemptedAt: null,
  lastSucceededAt: null,
  lastRef: null,
  lastStatus: null,
  lastMessage: null,
  lastCreated: 0,
  lastUpdated: 0,
  lastArchived: 0,
  lastUnchanged: 0,
  problems: [],
}

function renderPage(callerRole: WorkspaceRole = 'OWNER') {
  return renderWithProviders(
    <WorkspaceContext.Provider value={workspace(callerRole)}>
      <WorkspaceSettingsPage />
    </WorkspaceContext.Provider>,
  )
}

/**
 * The two git panels fetch on mount. Stubbed rather than left to fail, so this
 * file tests the settings form against a working page instead of against three
 * error states that happen not to be in the way.
 */
beforeEach(() => {
  vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue({
    enabled: false,
    exists: false,
    clonePath: null,
    sizeBytes: null,
  })
  vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(unconfiguredSync)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('WorkspaceSettingsPage', () => {
  it('seeds the form from the current workspace', () => {
    renderPage()

    expect(screen.getByLabelText('Name')).toHaveValue('Platform')
    expect(screen.getByLabelText('URL slug')).toHaveValue('platform')
    expect(screen.getByLabelText('Description')).toHaveValue('Core services')
  })

  /** Saving an unchanged form would be a pointless write. */
  it('keeps save disabled until something changes', async () => {
    renderPage()

    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Name'), ' Team')

    expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled()
  })

  it('saves the edited details', async () => {
    const update = vi
      .spyOn(endpoints.workspaceApi, 'update')
      .mockResolvedValue({ ...workspace(), name: 'Platform Team' })

    renderPage()
    await userEvent.type(screen.getByLabelText('Name'), ' Team')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith(
        'workspace-1',
        expect.objectContaining({ name: 'Platform Team', slug: 'platform' }),
      ),
    )
  })

  it('restores the original values when the edit is discarded', async () => {
    renderPage()

    await userEvent.type(screen.getByLabelText('Name'), ' Team')
    await userEvent.click(screen.getByRole('button', { name: 'Discard' }))

    expect(screen.getByLabelText('Name')).toHaveValue('Platform')
  })

  it('surfaces a field error from the backend', async () => {
    vi.spyOn(endpoints.workspaceApi, 'update').mockRejectedValue(
      new ApiError('Validation failed', 400, { slug: 'must be lowercase alphanumeric with hyphens' }),
    )

    renderPage()
    await userEvent.clear(screen.getByLabelText('URL slug'))
    await userEvent.type(screen.getByLabelText('URL slug'), 'Bad Slug')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(
      await screen.findByText('must be lowercase alphanumeric with hyphens'),
    ).toBeInTheDocument()
  })

  it('offers a slug derived from the name', async () => {
    renderPage()

    await userEvent.clear(screen.getByLabelText('Name'))
    await userEvent.type(screen.getByLabelText('Name'), 'Platform Team')

    await userEvent.click(screen.getByRole('button', { name: 'platform-team' }))

    expect(screen.getByLabelText('URL slug')).toHaveValue('platform-team')
  })

  /**
   * The two panels have different bars, so showing them together would be wrong
   * for one of them. Cloning needs viewer; deciding where documentation comes
   * from needs admin, and the server refuses to describe it to anyone else.
   */
  describe('git panels', () => {
    it('offers the remote to a member who cannot configure anything', async () => {
      vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue({
        enabled: true,
        exists: true,
        clonePath: '/git/ada/platform.git',
        sizeBytes: 1024,
      })

      renderPage('MEMBER')

      expect(await screen.findByLabelText('Remote URL')).toBeInTheDocument()
    })

    it('hides the sync settings from a member rather than showing them an error', async () => {
      vi.spyOn(endpoints.syncApi, 'repository').mockResolvedValue({
        enabled: true,
        exists: true,
        clonePath: '/git/ada/platform.git',
        sizeBytes: 1024,
      })

      renderPage('MEMBER')

      // Waiting on the other panel first is what makes the absence mean
      // something: asserting straight away would pass while the sync panel was
      // merely still loading, which it does whether or not it is allowed to.
      expect(await screen.findByLabelText('Remote URL')).toBeInTheDocument()
      expect(screen.queryByText('Sync from git')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Repository URL')).not.toBeInTheDocument()
    })

    it('shows the sync settings to an admin', async () => {
      renderPage('ADMIN')

      expect(await screen.findByText('Sync from git')).toBeInTheDocument()
    })
  })

  describe('deletion', () => {
    it('requires the workspace name to be typed out', async () => {
      renderPage('OWNER')

      await userEvent.click(screen.getByRole('button', { name: 'Delete workspace' }))

      const dialog = screen.getByRole('dialog', { name: 'Delete this workspace?' })
      const confirm = within(dialog).getByRole('button', { name: 'Delete workspace' })
      expect(confirm).toBeDisabled()

      await userEvent.type(screen.getByLabelText('Type "Platform" to confirm'), 'Platform')
      expect(confirm).toBeEnabled()
    })

    it('deletes once confirmed', async () => {
      const remove = vi.spyOn(endpoints.workspaceApi, 'remove').mockResolvedValue(undefined)

      renderPage('OWNER')
      await userEvent.click(screen.getByRole('button', { name: 'Delete workspace' }))
      await userEvent.type(screen.getByLabelText('Type "Platform" to confirm'), 'Platform')

      const dialog = screen.getByRole('dialog', { name: 'Delete this workspace?' })
      await userEvent.click(within(dialog).getByRole('button', { name: 'Delete workspace' }))

      await waitFor(() => expect(remove).toHaveBeenCalledWith('workspace-1'))
    })

    /** Matches the backend: only an owner may delete a workspace. */
    it('is not offered to an admin', () => {
      renderPage('ADMIN')

      expect(screen.queryByRole('button', { name: 'Delete workspace' })).not.toBeInTheDocument()
    })
  })

  describe('read-only roles', () => {
    it('disables the form for a member', () => {
      renderPage('MEMBER')

      expect(screen.getByLabelText('Name')).toBeDisabled()
      expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument()
    })

    it('explains why the form is disabled', () => {
      renderPage('VIEWER')

      expect(
        screen.getByText('Only admins and owners can change these details.'),
      ).toBeInTheDocument()
    })
  })
})
