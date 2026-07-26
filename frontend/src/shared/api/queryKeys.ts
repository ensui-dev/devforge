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

  instance: {
    /** Branding and policy. Read on every page, including before sign-in. */
    public: ['instance'] as const,
    settings: ['instance', 'settings'] as const,
    admins: ['instance', 'admins'] as const,
  },

  handbook: {
    contents: (handle: string, slug: string) => ['handbook', 'contents', handle, slug] as const,
  },

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
    /**
     * Page size is part of the key.
     *
     * Screens request the same workspace at different sizes — the overview wants
     * 5, the list 25, the document pickers 100. Leaving size out let those share
     * one cache entry, so whichever loaded first decided how many pages the
     * others believed in: opening the overview made the documents list show
     * "Page 1 of 5", and its Next button then asked for a range that did not
     * exist.
     */
    list: (workspaceId: string, documentType: DocumentType | 'ALL', page: number, size: number) =>
      ['workspaces', workspaceId, 'documents', 'list', documentType, page, size] as const,
    search: (workspaceId: string, query: string, page: number, size: number) =>
      ['workspaces', workspaceId, 'documents', 'search', query, page, size] as const,
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
