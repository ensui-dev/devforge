import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { RenderResult } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../shared/auth/AuthProvider'
import { ToastProvider } from '../shared/components/Toast'

interface RenderOptions {
  /** Initial history entries, for testing route-dependent screens. */
  route?: string
  withAuth?: boolean
}

/**
 * Renders a component inside the providers the app supplies at runtime.
 *
 * Retries are disabled so a test asserting an error state does not wait for a
 * retry cycle before the state appears.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', withAuth = false }: RenderOptions = {},
): RenderResult & { queryClient: QueryClient } {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })

  const wrap = (children: ReactNode) =>
    withAuth ? <AuthProvider>{children}</AuthProvider> : <>{children}</>

  const result = render(
    <QueryClientProvider client={queryClient}>
      {wrap(
        <ToastProvider>
          <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
        </ToastProvider>,
      )}
    </QueryClientProvider>,
  )

  return { ...result, queryClient }
}
