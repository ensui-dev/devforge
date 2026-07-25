import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { documentApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import type { DocumentPayload, DocumentType, ReferenceType } from '../../shared/types'

export function useDocumentList(
  workspaceId: string,
  options: { documentType: DocumentType | 'ALL'; page: number; search: string },
) {
  const { documentType, page, search } = options
  const trimmed = search.trim()
  const searching = trimmed.length > 0

  return useQuery({
    queryKey: searching
      ? queryKeys.documents.search(workspaceId, trimmed, page)
      : queryKeys.documents.list(workspaceId, documentType, page),
    queryFn: () =>
      searching
        ? documentApi.search(workspaceId, trimmed, { page })
        : documentApi.list(workspaceId, {
            documentType: documentType === 'ALL' ? undefined : documentType,
            page,
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
