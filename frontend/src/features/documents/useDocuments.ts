import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { documentApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import type { DocumentPayload, DocumentType, ReferenceType } from '../../shared/types'

/**
 * How many documents the list screen requests. Stated explicitly rather than
 * relying on the server default, so the value that goes into the cache key is the
 * same one that goes into the request.
 */
export const DOCUMENTS_PAGE_SIZE = 25

/**
 * How many documents a picker offers in one go. Large enough that the dialogs can
 * filter client-side without paging.
 */
export const DOCUMENT_PICKER_SIZE = 100

/** How many recent documents the workspace overview previews. */
export const OVERVIEW_DOCUMENT_COUNT = 5

export function useDocumentList(
  workspaceId: string,
  options: { documentType: DocumentType | 'ALL'; page: number; search: string },
) {
  const { documentType, page, search } = options
  const trimmed = search.trim()
  const searching = trimmed.length > 0
  const size = DOCUMENTS_PAGE_SIZE

  return useQuery({
    queryKey: searching
      ? queryKeys.documents.search(workspaceId, trimmed, page, size)
      : queryKeys.documents.list(workspaceId, documentType, page, size),
    queryFn: () =>
      searching
        ? documentApi.search(workspaceId, trimmed, { page, size })
        : documentApi.list(workspaceId, {
            documentType: documentType === 'ALL' ? undefined : documentType,
            page,
            size,
          }),
    // Keeps the previous page visible while the next loads, so paging and typing
    // do not blank the list.
    placeholderData: (previous) => previous,
  })
}

export function useDocument(workspaceId: string, documentId: string) {
  return useQuery({
    queryKey: queryKeys.documents.detail(workspaceId, documentId),
    queryFn: () => documentApi.get(workspaceId, documentId),
    enabled: Boolean(documentId),
  })
}

export function useDocumentReferences(workspaceId: string, documentId: string) {
  return useQuery({
    queryKey: queryKeys.documents.references(workspaceId, documentId),
    queryFn: () => documentApi.listReferences(workspaceId, documentId),
    enabled: Boolean(documentId),
  })
}

export function useCreateDocument(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: DocumentPayload) => documentApi.create(workspaceId, payload),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.documents.all(workspaceId) }),
  })
}

export function useUpdateDocument(workspaceId: string, documentId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: DocumentPayload) => documentApi.update(workspaceId, documentId, payload),
    onSuccess: () =>
      // Invalidating the subtree refreshes the detail view, every filtered list,
      // and any search results that included this document.
      queryClient.invalidateQueries({ queryKey: queryKeys.documents.all(workspaceId) }),
  })
}

export function useDeleteDocument(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: string) => documentApi.remove(workspaceId, documentId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.documents.all(workspaceId) }),
  })
}

export function useAddReference(workspaceId: string, documentId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { targetDocumentId: string; referenceType: ReferenceType }) =>
      documentApi.addReference(workspaceId, documentId, input.targetDocumentId, input.referenceType),
    onSuccess: (_result, input) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.documents.references(workspaceId, documentId),
      })
      // The far end gained a backlink, so its panel is stale too.
      queryClient.invalidateQueries({
        queryKey: queryKeys.documents.references(workspaceId, input.targetDocumentId),
      })
    },
  })
}

export function useRemoveReference(workspaceId: string, documentId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { referenceId: string; relatedDocumentId: string }) =>
      documentApi.removeReference(workspaceId, documentId, input.referenceId),
    onSuccess: (_result, input) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.documents.references(workspaceId, documentId),
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.documents.references(workspaceId, input.relatedDocumentId),
      })
    },
  })
}
