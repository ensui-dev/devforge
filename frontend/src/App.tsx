import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ApiError } from './shared/api/client'
import { AuthProvider } from './shared/auth/AuthProvider'
import { RequireAuth } from './shared/auth/RequireAuth'
import { ErrorBoundary } from './shared/components/Feedback'
import { ToastProvider } from './shared/components/Toast'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { WorkspaceListPage } from './features/workspaces/WorkspaceListPage'
import { WorkspaceLayout } from './features/workspaces/WorkspaceLayout'
import { WorkspaceOverviewPage } from './features/workspaces/WorkspaceOverviewPage'
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
        <ToastProvider>
          <BrowserRouter>
            <ErrorBoundary>
              <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />

                <Route element={<RequireAuth />}>
                  <Route path="/" element={<WorkspaceListPage />} />
                  <Route path="/workspaces/:workspaceId" element={<WorkspaceLayout />}>
                    <Route index element={<WorkspaceOverviewPage />} />
                    <Route path="documents" element={<DocumentListPage />} />
                    <Route path="documents/:documentId" element={<DocumentDetailPage />} />
                    <Route path="boards" element={<BoardListPage />} />
                    <Route path="boards/:boardId" element={<BoardPage />} />
                    <Route path="members" element={<MembersPage />} />
                  </Route>
                </Route>

                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </ErrorBoundary>
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
