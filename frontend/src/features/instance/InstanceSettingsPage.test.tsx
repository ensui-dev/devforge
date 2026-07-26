import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import type { AdminInstance, InstanceUser } from '../../shared/types'
import { InstanceSettingsPage } from './InstanceSettingsPage'

function settings(overrides: Partial<AdminInstance['instance']> = {}): AdminInstance {
  return {
    instance: {
      configured: true,
      name: 'Acme Docs',
      tagline: 'How Acme builds things',
      logoMark: '◆',
      logoImage: null,
      accentColor: '#7a3ea1',
      registrationMode: 'OPEN',
      allowedEmailDomains: [],
      publicDocsEnabled: true,
      handbookPath: 'ops/handbook',
      ...overrides,
    },
    allowedEmailDomains: null,
    publicBaseUrl: 'https://docs.acme.test',
    setupCompletedAt: '2026-01-01T00:00:00Z',
  }
}

function operator(id: string, displayName: string): InstanceUser {
  return {
    id,
    email: `${displayName.toLowerCase()}@acme.test`,
    displayName,
    handle: displayName.toLowerCase(),
    instanceAdmin: true,
  }
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('InstanceSettingsPage', () => {
  it('shows what the instance is currently configured to be', async () => {
    vi.spyOn(endpoints.instanceApi, 'settings').mockResolvedValue(settings())
    vi.spyOn(endpoints.instanceApi, 'administrators').mockResolvedValue([operator('u1', 'Ops')])

    renderWithProviders(<InstanceSettingsPage />, { withAuth: true })

    expect(await screen.findByLabelText('Name')).toHaveValue('Acme Docs')
    expect(screen.getByLabelText('Accent colour')).toHaveValue('#7a3ea1')
    expect(screen.getByLabelText('Public address')).toHaveValue('https://docs.acme.test')
    expect(screen.getByLabelText('Allow public documentation')).toBeChecked()
  })

  it('saves every setting as one change', async () => {
    vi.spyOn(endpoints.instanceApi, 'settings').mockResolvedValue(settings())
    vi.spyOn(endpoints.instanceApi, 'administrators').mockResolvedValue([operator('u1', 'Ops')])
    const update = vi.spyOn(endpoints.instanceApi, 'update').mockResolvedValue(settings())

    renderWithProviders(<InstanceSettingsPage />, { withAuth: true })

    const name = await screen.findByLabelText('Name')
    await userEvent.clear(name)
    await userEvent.type(name, 'Acme Engineering')
    await userEvent.click(screen.getByLabelText('Allow public documentation'))
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }))

    await waitFor(() => expect(update).toHaveBeenCalledTimes(1))
    expect(update.mock.calls[0][0]).toMatchObject({
      name: 'Acme Engineering',
      publicDocsEnabled: false,
      handbookPath: 'ops/handbook',
    })
  })

  /** The domain list only exists in one mode, and is required there. */
  it('asks for domains only when registration is restricted', async () => {
    vi.spyOn(endpoints.instanceApi, 'settings').mockResolvedValue(settings())
    vi.spyOn(endpoints.instanceApi, 'administrators').mockResolvedValue([operator('u1', 'Ops')])

    renderWithProviders(<InstanceSettingsPage />, { withAuth: true })

    expect(await screen.findByLabelText('Registration')).toBeInTheDocument()
    expect(screen.queryByLabelText('Allowed email domains')).not.toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Registration'), 'RESTRICTED')
    expect(screen.getByLabelText('Allowed email domains')).toBeInTheDocument()
  })

  it('explains how to add people once registration is closed', async () => {
    vi.spyOn(endpoints.instanceApi, 'settings').mockResolvedValue(
      settings({ registrationMode: 'CLOSED' }),
    )
    vi.spyOn(endpoints.instanceApi, 'administrators').mockResolvedValue([operator('u1', 'Ops')])

    renderWithProviders(<InstanceSettingsPage />, { withAuth: true })

    expect(await screen.findByText(/sign-up form refuses everyone/)).toBeInTheDocument()
  })

  /**
   * The lockout the server also refuses. Showing an enabled control that always
   * fails would be worse than showing why it is disabled.
   */
  it('will not let the only operator step down', async () => {
    vi.spyOn(endpoints.instanceApi, 'settings').mockResolvedValue(settings())
    vi.spyOn(endpoints.instanceApi, 'administrators').mockResolvedValue([operator('u1', 'Ops')])

    renderWithProviders(<InstanceSettingsPage />, { withAuth: true })

    expect(await screen.findByRole('button', { name: 'Remove' })).toBeDisabled()
    expect(
      screen.getByText('Nobody can be removed until there is a second one.'),
    ).toBeInTheDocument()
  })

  it('lets an operator be removed once there is a second one', async () => {
    vi.spyOn(endpoints.instanceApi, 'settings').mockResolvedValue(settings())
    vi.spyOn(endpoints.instanceApi, 'administrators').mockResolvedValue([
      operator('u1', 'Ops'),
      operator('u2', 'Second'),
    ])
    const setAdmin = vi
      .spyOn(endpoints.instanceApi, 'setInstanceAdmin')
      .mockResolvedValue({ ...operator('u2', 'Second'), instanceAdmin: false })

    renderWithProviders(<InstanceSettingsPage />, { withAuth: true })

    const buttons = await screen.findAllByRole('button', { name: 'Remove' })
    await userEvent.click(buttons[1])

    await waitFor(() => expect(setAdmin).toHaveBeenCalledWith('u2', false))
  })

  it('reports a failure to load rather than showing an empty form', async () => {
    vi.spyOn(endpoints.instanceApi, 'settings').mockRejectedValue(new Error('nope'))
    vi.spyOn(endpoints.instanceApi, 'administrators').mockResolvedValue([])

    renderWithProviders(<InstanceSettingsPage />, { withAuth: true })

    expect(await screen.findByText('Could not load the instance settings')).toBeInTheDocument()
    expect(screen.queryByLabelText('Name')).not.toBeInTheDocument()
  })
})
