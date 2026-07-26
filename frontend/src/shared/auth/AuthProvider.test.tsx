import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

const STORAGE_KEY = 'devforge.session'

function Probe() {
  const { user, isAuthenticated, isInitialising, logIn, logOut } = useAuth()

  if (isInitialising) {
    return <p>initialising</p>
  }

  return (
    <div>
      <p data-testid="state">{isAuthenticated ? `signed in as ${user?.displayName}` : 'signed out'}</p>
      <button type="button" onClick={() => void logIn({ email: 'dev@example.com', password: 'pw' })}>
        Sign in
      </button>
      <button type="button" onClick={logOut}>
        Sign out
      </button>
    </div>
  )
}

function storedSession(expiresAt: string) {
  return JSON.stringify({
    accessToken: 'token-abc',
    expiresAt,
    user: {
      id: 'user-1',
      email: 'dev@example.com',
      displayName: 'Stored Dev',
      handle: 'dev',
    },
  })
}

function loginResponse() {
  return new Response(
    JSON.stringify({
      accessToken: 'token-xyz',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: {
        id: 'user-1',
        email: 'dev@example.com',
        displayName: 'Fresh Dev',
        handle: 'dev',
      },
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  )
}

describe('AuthProvider', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('starts signed out with no stored session', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('signed out'))
  })

  it('restores a valid stored session', async () => {
    window.localStorage.setItem(
      STORAGE_KEY,
      storedSession(new Date(Date.now() + 3_600_000).toISOString()),
    )

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('signed in as Stored Dev'),
    )
  })

  /** An expired token should be dropped locally, not sent and rejected. */
  it('discards an expired stored session', async () => {
    window.localStorage.setItem(
      STORAGE_KEY,
      storedSession(new Date(Date.now() - 1_000).toISOString()),
    )

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('signed out'))
    expect(window.localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('discards a corrupt stored session instead of crashing', async () => {
    window.localStorage.setItem(STORAGE_KEY, 'not json')

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('signed out'))
    expect(window.localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('stores the session after signing in', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(loginResponse()))

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('state')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('signed in as Fresh Dev'),
    )
    expect(window.localStorage.getItem(STORAGE_KEY)).toContain('token-xyz')
  })

  it('clears the stored session on sign out', async () => {
    window.localStorage.setItem(
      STORAGE_KEY,
      storedSession(new Date(Date.now() + 3_600_000).toISOString()),
    )

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('signed in as Stored Dev'),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('signed out'))
    expect(window.localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('attaches the restored token to subsequent requests', async () => {
    window.localStorage.setItem(
      STORAGE_KEY,
      storedSession(new Date(Date.now() + 3_600_000).toISOString()),
    )
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    vi.stubGlobal('fetch', fetchMock)

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('signed in as Stored Dev'),
    )

    const { apiRequest } = await import('../api/client')
    await apiRequest('/api/workspaces')

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-abc')
  })
})
