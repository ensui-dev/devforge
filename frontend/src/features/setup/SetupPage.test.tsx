import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import { ApiError } from '../../shared/api/client'
import type { Instance, SetupResult } from '../../shared/types'
import { SetupPage } from './SetupPage'

const configured: Instance = {
  configured: true,
  name: 'Acme Docs',
  tagline: 'How Acme builds things',
  logoMark: '◆',
  logoImage: null,
  accentColor: '#7a3ea1',
  registrationMode: 'RESTRICTED',
  allowedEmailDomains: ['acme.test'],
  publicDocsEnabled: true,
  handbookPath: null,
}

const result: SetupResult = { instance: configured, adminEmail: 'ops@acme.test' }

/** Fills the four steps and stops on the last one, without submitting. */
async function walkToOperator(options: { name?: string; mode?: string } = {}) {
  await userEvent.type(screen.getByLabelText('Instance name'), options.name ?? 'Acme Docs')
  await userEvent.click(screen.getByRole('button', { name: 'Continue' }))
  await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

  // Access. The default is Restricted, which needs a domain before it will move on.
  await userEvent.selectOptions(screen.getByLabelText('Registration'), options.mode ?? 'OPEN')
  await userEvent.click(screen.getByRole('button', { name: 'Continue' }))
}

async function fillOperator(password = 'password123', confirm = 'password123') {
  await userEvent.type(screen.getByLabelText('Name'), 'Acme Ops')
  await userEvent.type(screen.getByLabelText('Email'), 'ops@acme.test')
  await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.type(screen.getByLabelText('Confirm password'), confirm)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('SetupPage', () => {
  it('will not leave the first step without a name for the instance', async () => {
    const setUp = vi.spyOn(endpoints.instanceApi, 'setUp')

    renderWithProviders(<SetupPage />, { withAuth: true })
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText('Give this instance a name.')).toBeInTheDocument()
    // Still on step one, so the appearance controls have not appeared.
    expect(screen.queryByLabelText('Mark')).not.toBeInTheDocument()
    expect(setUp).not.toHaveBeenCalled()
  })

  /**
   * The trap this catches: a restricted instance with no domains listed accepts
   * nobody, and the server refuses the settings outright.
   */
  it('will not choose restricted registration without naming a domain', async () => {
    renderWithProviders(<SetupPage />, { withAuth: true })

    await userEvent.type(screen.getByLabelText('Instance name'), 'Acme Docs')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(
      await screen.findByText('List at least one domain, or choose a different mode.'),
    ).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Allowed email domains'), 'acme.test')
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByLabelText('Confirm password')).toBeInTheDocument()
  })

  it('sends the whole configuration and the first operator account', async () => {
    const setUp = vi.spyOn(endpoints.instanceApi, 'setUp').mockResolvedValue(result)
    vi.spyOn(endpoints.authApi, 'login').mockRejectedValue(new Error('not under test'))

    renderWithProviders(<SetupPage />, { withAuth: true })
    await walkToOperator()
    await fillOperator()
    await userEvent.click(screen.getByRole('button', { name: 'Finish setup' }))

    await waitFor(() => expect(setUp).toHaveBeenCalledTimes(1))
    expect(setUp.mock.calls[0][0]).toEqual({
      instance: {
        name: 'Acme Docs',
        tagline: '',
        logoMark: '⌁',
        logoImage: '',
        accentColor: '#0e6b73',
        registrationMode: 'OPEN',
        allowedEmailDomains: '',
        publicDocsEnabled: true,
        handbookPath: '',
        publicBaseUrl: '',
      },
      admin: {
        email: 'ops@acme.test',
        displayName: 'Acme Ops',
        password: 'password123',
      },
    })
  })

  /** Setup is one-shot, so a typo here would lock the operator out of their own instance. */
  it('refuses to create the account when the two passwords differ', async () => {
    const setUp = vi.spyOn(endpoints.instanceApi, 'setUp').mockResolvedValue(result)

    renderWithProviders(<SetupPage />, { withAuth: true })
    await walkToOperator()
    await fillOperator('password123', 'password124')
    await userEvent.click(screen.getByRole('button', { name: 'Finish setup' }))

    expect(await screen.findByText('The two passwords do not match.')).toBeInTheDocument()
    expect(setUp).not.toHaveBeenCalled()
  })

  it('signs the new operator in and opens their workspaces', async () => {
    vi.spyOn(endpoints.instanceApi, 'setUp').mockResolvedValue(result)
    const login = vi.spyOn(endpoints.authApi, 'login').mockResolvedValue({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: {
        id: 'user-1',
        email: 'ops@acme.test',
        displayName: 'Acme Ops',
        handle: 'ops',
        instanceAdmin: true,
      },
    })

    renderWithProviders(<SetupPage />, { withAuth: true })
    await walkToOperator()
    await fillOperator()
    await userEvent.click(screen.getByRole('button', { name: 'Finish setup' }))

    await waitFor(() =>
      expect(login).toHaveBeenCalledWith({ email: 'ops@acme.test', password: 'password123' }),
    )
  })

  it('puts a rejected field beside the input that caused it', async () => {
    vi.spyOn(endpoints.instanceApi, 'setUp').mockRejectedValue(
      new ApiError('Validation failed', 400, {
        'admin.email': 'An account already exists for ops@acme.test',
      }),
    )

    renderWithProviders(<SetupPage />, { withAuth: true })
    await walkToOperator()
    await fillOperator()
    await userEvent.click(screen.getByRole('button', { name: 'Finish setup' }))

    expect(
      await screen.findByText('An account already exists for ops@acme.test'),
    ).toBeInTheDocument()
  })
})
