import type { DocumentType } from '../types'

/**
 * Every TanStack Query key in one place.
 *
 * Keys are hierarchical so a mutation can invalidate a whole subtree — writing a
 * document invalidates `documents.all(workspaceId)` and every filtered or
 * searched list beneath it refreshes, without each call site having to remember
 * the variants.
 */
export const queryKeys = {
  currentUser: ['currentUser'] as const,

  workspaces: {
    all: ['workspaces'] as const,
    detail: (workspaceId: string) => ['workspaces', workspaceId] as const,
  },

  members: {
    all: (workspaceId: string) => ['workspaces', workspaceId, 'members'] as const,
  },

  users: {
    search: (query: string) => ['users', 'search', query] as const,
  },

  documents: {
    all: (workspaceId: string) => ['workspaces', workspaceId, 'documents'] as const,
    list: (workspaceId: string, documentType: DocumentType | 'ALL', page: number) =>
      ['workspaces', workspaceId, 'documents', 'list', documentType, page] as const,
    search: (workspaceId: string, query: string, page: number) =>
      ['workspaces', workspaceId, 'documents', 'search', query, page] as const,
    detail: (workspaceId: string, documentId: string) =>
      ['workspaces', workspaceId, 'documents', 'detail', documentId] as const,
    references: (workspaceId: string, documentId: string) =>
      ['workspaces', workspaceId, 'documents', 'detail', documentId, 'references'] as const,
  },

  boards: {
    all: (workspaceId: string) => ['workspaces', workspaceId, 'boards'] as const,
    detail: (workspaceId: string, boardId: string) =>
      ['workspaces', workspaceId, 'boards', boardId] as const,
  },
}
