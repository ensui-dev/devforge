import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../shared/api/queryKeys'
import { workspaceApi } from '../../shared/api/endpoints'
import type { WorkspacePayload } from '../../shared/types'

export function useWorkspaces() {
  return useQuery({
    queryKey: queryKeys.workspaces.all,
    queryFn: workspaceApi.list,
  })
}

export function useWorkspace(workspaceId: string) {
  return useQuery({
    queryKey: queryKeys.workspaces.detail(workspaceId),
    queryFn: () => workspaceApi.get(workspaceId),
    enabled: Boolean(workspaceId),
  })
}

export function useCreateWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: WorkspacePayload) => workspaceApi.create(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.workspaces.all }),
  })
}

export function useUpdateWorkspace(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: WorkspacePayload) => workspaceApi.update(workspaceId, payload),
    onSuccess: () => {
      // Both the list and the detail view show the name and slug.
      queryClient.invalidateQueries({ queryKey: queryKeys.workspaces.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.workspaces.detail(workspaceId) })
    },
  })
}

export function useDeleteWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (workspaceId: string) => workspaceApi.remove(workspaceId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.workspaces.all }),
  })
}
