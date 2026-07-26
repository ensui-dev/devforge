import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ApiError } from './shared/api/client'
import { AuthProvider } from './shared/auth/AuthProvider'
import { RequireAuth } from './shared/auth/RequireAuth'
import { InstanceProvider } from './shared/instance/InstanceProvider'
import { InstanceGate } from './shared/instance/InstanceGate'
import { ErrorBoundary } from './shared/components/Feedback'
import { ToastProvider } from './shared/components/Toast'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { HomePage } from './features/site/HomePage'
import { SetupPage } from './features/setup/SetupPage'
import { InstanceSettingsPage } from './features/instance/InstanceSettingsPage'
import { DocsPage } from './features/site/DocsPage'
import { WorkspaceListPage } from './features/workspaces/WorkspaceListPage'
import { WorkspaceLayout } from './features/workspaces/WorkspaceLayout'
import { WorkspaceOverviewPage } from './features/workspaces/WorkspaceOverviewPage'
import { WorkspaceSettingsPage } from './features/workspaces/WorkspaceSettingsPage'
import { DocumentListPage } from './features/documents/DocumentListPage'
import { DocumentDetailPage } from './features/documents/DocumentDetailPage'
import { BoardListPage } from './features/boards/BoardListPage'
import { BoardPage } from './features/boards/BoardPage'
import { MembersPage } from './features/members/MembersPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Retrying a 401 or a 404 only delays the real outcome; retry once for
      // anything that might genuinely be transient.
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status < 500) {
          return false
        }
        return failureCount < 1
      },
      refetchOnWindowFocus: false,
    },
  },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <InstanceProvider>
          <ToastProvider>
            <BrowserRouter>
              <ErrorBoundary>
                {/* A deployment nobody has claimed shows only its setup screen. */}
                <InstanceGate>
                  <Routes>
                    {/* First run, on an instance that has no accounts at all. */}
                    <Route path="/setup" element={<SetupPage />} />

                    {/* Public: readable before anyone signs up. */}
                    <Route path="/" element={<HomePage />} />
                    <Route path="/docs" element={<DocsPage />} />
                    <Route path="/docs/:handle" element={<DocsPage />} />
                    <Route path="/docs/:handle/:workspaceSlug" element={<DocsPage />} />
                    <Route path="/docs/:handle/:workspaceSlug/:slug" element={<DocsPage />} />
                    <Route path="/login" element={<LoginPage />} />
                    <Route path="/register" element={<RegisterPage />} />

                    {/* The application itself. */}
                    <Route element={<RequireAuth />}>
                      <Route path="/app" element={<WorkspaceListPage />} />
                      {/* Guarded again by the server, which refuses anyone who is
                          not an instance administrator. */}
                      <Route path="/instance" element={<InstanceSettingsPage />} />
                      <Route path="/workspaces/:workspaceId" element={<WorkspaceLayout />}>
                        <Route index element={<WorkspaceOverviewPage />} />
                        <Route path="documents" element={<DocumentListPage />} />
                        <Route path="documents/:documentId" element={<DocumentDetailPage />} />
                        <Route path="boards" element={<BoardListPage />} />
                        <Route path="boards/:boardId" element={<BoardPage />} />
                        <Route path="members" element={<MembersPage />} />
                        <Route path="settings" element={<WorkspaceSettingsPage />} />
                      </Route>
                    </Route>

                    <Route path="*" element={<Navigate to="/" replace />} />
                  </Routes>
                </InstanceGate>
              </ErrorBoundary>
            </BrowserRouter>
          </ToastProvider>
        </InstanceProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
