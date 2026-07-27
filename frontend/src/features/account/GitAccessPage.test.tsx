import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import type { GitAccessToken } from '../../shared/types'
import { GitAccessPage } from './GitAccessPage'

function token(overrides: Partial<GitAccessToken> = {}): GitAccessToken {
  return {
    id: 't1',
    name: 'Work laptop',
    hint: 'dfg_abc1234',
    createdAt: new Date(Date.now() - 3_600_000).toISOString(),
    lastUsedAt: null,
    expiresAt: null,
    expired: false,
    ...overrides,
  }
}

function render() {
  return renderWithProviders(<GitAccessPage />, { withAuth: true })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('GitAccessPage', () => {
  it('invites a first token rather than showing an empty table', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([])

    render()

    expect(await screen.findByText(/No tokens yet/)).toBeInTheDocument()
  })

  it('lists a token by name and hint, never by value', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([token()])

    render()

    expect(await screen.findByText('Work laptop')).toBeInTheDocument()
    expect(screen.getByText('dfg_abc1234…')).toBeInTheDocument()
    expect(screen.getByText(/never used/)).toBeInTheDocument()
  })

  it('marks an expired token so it is not mistaken for a working one', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([
      token({ expired: true, expiresAt: new Date(Date.now() - 86_400_000).toISOString() }),
    ])

    render()

    expect(await screen.findByText('Expired')).toBeInTheDocument()
  })

  /** The one moment the secret exists; the dialog has to say it will not return. */
  it('shows the secret once, in a dialog that says so', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([])
    const create = vi.spyOn(endpoints.gitTokenApi, 'create').mockResolvedValue({
      token: token(),
      secret: 'dfg_abc1234_the_rest_of_it',
    })

    render()
    await userEvent.type(await screen.findByLabelText('What is it for'), 'Work laptop')
    await userEvent.click(screen.getByRole('button', { name: 'Issue token' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith({ name: 'Work laptop' }))
    expect(await screen.findByText('dfg_abc1234_the_rest_of_it')).toBeInTheDocument()
    expect(screen.getByText(/only time it can be read/)).toBeInTheDocument()
  })

  it('sends the chosen expiry', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([])
    const create = vi.spyOn(endpoints.gitTokenApi, 'create').mockResolvedValue({
      token: token(),
      secret: 'dfg_x',
    })

    render()
    await userEvent.type(await screen.findByLabelText('What is it for'), 'CI')
    await userEvent.selectOptions(screen.getByLabelText('Expires'), '90')
    await userEvent.click(screen.getByRole('button', { name: 'Issue token' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith({ name: 'CI', expiresInDays: 90 }),
    )
  })

  it('will not issue a token with no name', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([])

    render()

    expect(await screen.findByRole('button', { name: 'Issue token' })).toBeDisabled()
  })

  /** Revoking cuts off whatever is using it, so it is confirmed first. */
  it('confirms before revoking, naming what will stop working', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([token()])
    const revoke = vi.spyOn(endpoints.gitTokenApi, 'revoke').mockResolvedValue(undefined)

    render()
    await userEvent.click(await screen.findByRole('button', { name: 'Revoke' }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Work laptop')).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Revoke' }))
    await waitFor(() => expect(revoke).toHaveBeenCalledWith('t1'))
  })

  it('does not revoke when the confirmation is dismissed', async () => {
    vi.spyOn(endpoints.gitTokenApi, 'list').mockResolvedValue([token()])
    const revoke = vi.spyOn(endpoints.gitTokenApi, 'revoke').mockResolvedValue(undefined)

    render()
    await userEvent.click(await screen.findByRole('button', { name: 'Revoke' }))
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    )

    expect(revoke).not.toHaveBeenCalled()
  })
})
