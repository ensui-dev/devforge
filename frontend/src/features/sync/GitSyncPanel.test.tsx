import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import { ApiError } from '../../shared/api/client'
import type { SyncSettings } from '../../shared/types'
import { GitSyncPanel } from './GitSyncPanel'

function settings(overrides: Partial<SyncSettings> = {}): SyncSettings {
  return {
    configured: true,
    repositoryUrl: 'https://github.com/you/handbook',
    branch: 'main',
    documentPath: 'docs',
    defaultType: 'GENERAL',
    deletionPolicy: 'ARCHIVE',
    enabled: true,
    hasAccessToken: false,
    hasWebhookSecret: false,
    webhookUrl: '/api/public/sync/11111111-2222-3333-4444-555555555555',
    webhookId: '11111111-2222-3333-4444-555555555555',
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
    ...overrides,
  }
}

const unconfigured = settings({
  configured: false,
  repositoryUrl: '',
  documentPath: '',
  webhookUrl: null,
  webhookId: null,
})

function render() {
  return renderWithProviders(<GitSyncPanel workspaceId="w1" />, { withAuth: true })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('GitSyncPanel', () => {
  it('offers the form but no webhook section before a repository is set', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(unconfigured)

    render()

    expect(await screen.findByLabelText('Repository URL')).toHaveValue('')
    // Nothing to wire a webhook to yet, and nothing to sync.
    expect(screen.queryByRole('button', { name: 'Sync now' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Webhook URL')).not.toBeInTheDocument()
  })

  it('shows the stored configuration', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings())

    render()

    expect(await screen.findByLabelText('Repository URL')).toHaveValue(
      'https://github.com/you/handbook',
    )
    expect(screen.getByLabelText('Branch')).toHaveValue('main')
    expect(screen.getByLabelText('Documentation folder')).toHaveValue('docs')
    expect(screen.getByLabelText('Syncing enabled')).toBeChecked()
  })

  it('saves the settings', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(unconfigured)
    const save = vi.spyOn(endpoints.syncApi, 'save').mockResolvedValue(settings())

    render()
    await userEvent.type(
      await screen.findByLabelText('Repository URL'),
      'https://github.com/you/handbook',
    )
    await userEvent.type(screen.getByLabelText('Documentation folder'), 'docs')
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }))

    await waitFor(() => expect(save).toHaveBeenCalledTimes(1))
    expect(save.mock.calls[0][1]).toMatchObject({
      repositoryUrl: 'https://github.com/you/handbook',
      documentPath: 'docs',
      deletionPolicy: 'ARCHIVE',
      enabled: true,
    })
  })

  /**
   * The form cannot echo a stored token back, so it must not send an empty one
   * either — that would clear a credential the operator never touched.
   */
  it('omits the access token when the field was left alone', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings({ hasAccessToken: true }))
    const save = vi.spyOn(endpoints.syncApi, 'save').mockResolvedValue(settings())

    render()
    await userEvent.clear(await screen.findByLabelText('Branch'))
    await userEvent.type(screen.getByLabelText('Branch'), 'release')
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }))

    await waitFor(() => expect(save).toHaveBeenCalled())
    expect(save.mock.calls[0][1]).not.toHaveProperty('accessToken')
  })

  it('sends the access token when one is typed', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings())
    const save = vi.spyOn(endpoints.syncApi, 'save').mockResolvedValue(settings())

    render()
    await userEvent.type(await screen.findByLabelText('Access token'), 'ghp_token')
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }))

    await waitFor(() => expect(save).toHaveBeenCalled())
    expect(save.mock.calls[0][1]).toMatchObject({ accessToken: 'ghp_token' })
  })

  /** So an operator can tell whether a token is stored without it being shown. */
  it('says when a token is already stored', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings({ hasAccessToken: true }))

    render()

    expect(await screen.findByLabelText('Replace access token')).toBeInTheDocument()
  })

  it('runs a sync on request and reports the outcome', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings())
    const run = vi.spyOn(endpoints.syncApi, 'run').mockResolvedValue(
      settings({
        lastStatus: 'OK',
        lastAttemptedAt: '2026-07-27T10:00:00Z',
        lastRef: 'abc1234',
        lastMessage: '2 created, 0 updated, 0 withdrawn, 0 unchanged',
        lastCreated: 2,
      }),
    )

    render()
    await userEvent.click(await screen.findByRole('button', { name: 'Sync now' }))

    await waitFor(() => expect(run).toHaveBeenCalledWith('w1'))
    // Scoped to the status panel: the toast repeats the same message.
    const status = within(await screen.findByRole('region', { name: 'Last sync' }))
    expect(status.getByText('Succeeded')).toBeInTheDocument()
    expect(status.getByText(/2 created/)).toBeInTheDocument()
  })

  it('shows a failure with the reason', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(
      settings({
        lastStatus: 'FAILED',
        lastAttemptedAt: '2026-07-27T10:00:00Z',
        lastMessage: 'That repository is private. Add an access token with read access.',
      }),
    )

    render()

    expect(await screen.findByText('Failed')).toBeInTheDocument()
    expect(screen.getByText(/That repository is private/)).toBeInTheDocument()
  })

  it('lists the files a partial sync could not use', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(
      settings({
        lastStatus: 'PARTIAL',
        lastAttemptedAt: '2026-07-27T10:00:00Z',
        lastMessage: '3 created, 0 updated, 0 withdrawn, 0 unchanged, 1 file(s) skipped',
        problems: ["docs/odd.md: unknown document type 'SPREADSHEET'; used GENERAL"],
      }),
    )

    render()

    expect(await screen.findByText('Partly applied')).toBeInTheDocument()
    expect(screen.getByText(/SPREADSHEET/)).toBeInTheDocument()
  })

  /** A failing sync that used to work is a different situation from one that never did. */
  it('says when it last succeeded, if it is failing now', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(
      settings({
        lastStatus: 'FAILED',
        lastAttemptedAt: '2026-07-27T10:00:00Z',
        lastSucceededAt: '2026-07-20T10:00:00Z',
        lastMessage: 'Could not reach the host',
      }),
    )

    render()

    expect(await screen.findByText(/Last succeeded/)).toBeInTheDocument()
  })

  // ------------------------------------------------------------------- webhook

  it('shows the absolute webhook URL a git host needs', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings())

    render()

    const url = await screen.findByLabelText('Webhook URL')
    expect(url).toHaveValue(
      `${window.location.origin}/api/public/sync/11111111-2222-3333-4444-555555555555`,
    )
    expect(url).toHaveAttribute('readonly')
  })

  /** Deliveries are refused without a secret, so the panel has to say so. */
  it('warns that deliveries are refused until a secret exists', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings({ hasWebhookSecret: false }))

    render()

    expect(await screen.findByText(/deliveries are refused/)).toBeInTheDocument()
  })

  it('does not warn once a secret is stored', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings({ hasWebhookSecret: true }))

    render()

    await screen.findByLabelText('Webhook URL')
    expect(screen.queryByText(/deliveries are refused/)).not.toBeInTheDocument()
  })

  /**
   * The secret is stored encrypted, so this response is the only chance to read it.
   * The panel must show it and say that plainly.
   */
  it('shows a generated secret once, and says it cannot be shown again', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings())
    vi.spyOn(endpoints.syncApi, 'generateSecret').mockResolvedValue({
      webhookSecret: 'a-generated-secret-value',
    })

    render()
    await userEvent.click(await screen.findByRole('button', { name: 'Generate secret' }))

    expect(await screen.findByText('a-generated-secret-value')).toBeInTheDocument()
    expect(screen.getByText(/only time it can be read/)).toBeInTheDocument()
  })

  it('offers to replace an existing secret rather than to create one', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings({ hasWebhookSecret: true }))

    render()

    expect(await screen.findByRole('button', { name: 'Generate a new secret' })).toBeInTheDocument()
  })

  it('changes the webhook URL on request', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings())
    const rotate = vi.spyOn(endpoints.syncApi, 'rotateUrl').mockResolvedValue(
      settings({ webhookUrl: '/api/public/sync/99999999-9999-9999-9999-999999999999' }),
    )

    render()
    await userEvent.click(await screen.findByRole('button', { name: 'Change the URL' }))

    await waitFor(() => expect(rotate).toHaveBeenCalledWith('w1'))
    expect(await screen.findByLabelText('Webhook URL')).toHaveValue(
      `${window.location.origin}/api/public/sync/99999999-9999-9999-9999-999999999999`,
    )
  })

  // ---------------------------------------------------------------- disconnect

  it('confirms before disconnecting, and says what survives', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(settings())
    const disconnect = vi.spyOn(endpoints.syncApi, 'disconnect').mockResolvedValue(undefined)

    render()
    await userEvent.click(await screen.findByRole('button', { name: 'Disconnect' }))

    const dialog = screen.getByRole('dialog', { name: 'Disconnect this repository?' })
    expect(within(dialog).getByText(/documents already synced are not touched/)).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Disconnect' }))
    await waitFor(() => expect(disconnect).toHaveBeenCalledWith('w1'))
  })

  it('puts a rejected field beside the input that caused it', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockResolvedValue(unconfigured)
    vi.spyOn(endpoints.syncApi, 'save').mockRejectedValue(
      new ApiError('Validation failed', 400, { repositoryUrl: 'must be an http(s) URL' }),
    )

    render()
    await userEvent.type(await screen.findByLabelText('Repository URL'), 'git@github.com:a/b.git')
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }))

    expect(await screen.findByText('must be an http(s) URL')).toBeInTheDocument()
  })

  it('reports a failure to load rather than an empty panel', async () => {
    vi.spyOn(endpoints.syncApi, 'get').mockRejectedValue(new Error('nope'))

    render()

    expect(await screen.findByText('Could not load the sync settings')).toBeInTheDocument()
  })
})
