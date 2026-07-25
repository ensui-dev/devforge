import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { boardApi, taskApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import type {
  Board,
  ColumnPayload,
  CreateTaskPayload,
  MoveTaskPayload,
  UpdateTaskPayload,
} from '../../shared/types'

export function useBoardList(workspaceId: string) {
  return useQuery({
    queryKey: queryKeys.boards.all(workspaceId),
    queryFn: () => boardApi.list(workspaceId),
  })
}

export function useBoard(workspaceId: string, boardId: string) {
  return useQuery({
    queryKey: queryKeys.boards.detail(workspaceId, boardId),
    queryFn: () => boardApi.get(workspaceId, boardId),
    enabled: Boolean(boardId),
  })
}

export function useCreateBoard(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => boardApi.create(workspaceId, name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.boards.all(workspaceId) }),
  })
}

export function useDeleteBoard(workspaceId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (boardId: string) => boardApi.remove(workspaceId, boardId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.boards.all(workspaceId) }),
  })
}

/**
 * Column mutations all return the whole board, so the cache is replaced rather
 * than invalidated — the response is already authoritative, and refetching would
 * make reordering feel laggy.
 */
function useBoardMutation<TInput>(
  workspaceId: string,
  boardId: string,
  mutationFn: (input: TInput) => Promise<Board>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (board) => {
      queryClient.setQueryData(queryKeys.boards.detail(workspaceId, boardId), board)
      // Counts on the board list may have changed.
      queryClient.invalidateQueries({ queryKey: queryKeys.boards.all(workspaceId) })
    },
  })
}

export function useRenameBoard(workspaceId: string, boardId: string) {
  return useBoardMutation<string>(workspaceId, boardId, (name) =>
    boardApi.rename(workspaceId, boardId, name),
  )
}

export function useAddColumn(workspaceId: string, boardId: string) {
  return useBoardMutation<ColumnPayload>(workspaceId, boardId, (payload) =>
    boardApi.addColumn(workspaceId, boardId, payload),
  )
}

export function useUpdateColumn(workspaceId: string, boardId: string) {
  return useBoardMutation<{ columnId: string; payload: ColumnPayload }>(
    workspaceId,
    boardId,
    ({ columnId, payload }) => boardApi.updateColumn(workspaceId, boardId, columnId, payload),
  )
}

export function useMoveColumn(workspaceId: string, boardId: string) {
  return useBoardMutation<{ columnId: string; position: number }>(
    workspaceId,
    boardId,
    ({ columnId, position }) => boardApi.moveColumn(workspaceId, boardId, columnId, position),
  )
}

export function useRemoveColumn(workspaceId: string, boardId: string) {
  return useBoardMutation<string>(workspaceId, boardId, (columnId) =>
    boardApi.removeColumn(workspaceId, boardId, columnId),
  )
}

/** Task mutations change one card, so the board is refetched to pick up positions. */
function useTaskMutation<TInput, TResult>(
  workspaceId: string,
  boardId: string,
  mutationFn: (input: TInput) => Promise<TResult>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.boards.detail(workspaceId, boardId) })
      queryClient.invalidateQueries({ queryKey: queryKeys.boards.all(workspaceId) })
    },
  })
}

export function useCreateTask(workspaceId: string, boardId: string) {
  return useTaskMutation<CreateTaskPayload, unknown>(workspaceId, boardId, (payload) =>
    taskApi.create(workspaceId, boardId, payload),
  )
}

export function useUpdateTask(workspaceId: string, boardId: string) {
  return useTaskMutation<{ taskId: string; payload: UpdateTaskPayload }, unknown>(
    workspaceId,
    boardId,
    ({ taskId, payload }) => taskApi.update(workspaceId, boardId, taskId, payload),
  )
}

export function useMoveTask(workspaceId: string, boardId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { taskId: string; payload: MoveTaskPayload }) =>
      taskApi.move(workspaceId, boardId, input.taskId, input.payload),
    /**
     * Moves are applied to the cache first so a drag lands instantly. The
     * previous board is kept so a rejected move — a WIP limit, say — snaps back
     * rather than leaving the UI showing a state the server refused.
     */
    onMutate: async ({ taskId, payload }) => {
      const key = queryKeys.boards.detail(workspaceId, boardId)
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<Board>(key)

      if (previous) {
        queryClient.setQueryData<Board>(key, applyMoveLocally(previous, taskId, payload))
      }

      return { previous }
    },
    onError: (_error, _input, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKeys.boards.detail(workspaceId, boardId), context.previous)
      }
    },
    onSettled: () => {
      // Reconcile with the server's authoritative positions.
      queryClient.invalidateQueries({ queryKey: queryKeys.boards.detail(workspaceId, boardId) })
    },
  })
}

export function useDeleteTask(workspaceId: string, boardId: string) {
  return useTaskMutation<string, void>(workspaceId, boardId, (taskId) =>
    taskApi.remove(workspaceId, boardId, taskId),
  )
}

export function useLinkTaskDocument(workspaceId: string, boardId: string) {
  return useTaskMutation<{ taskId: string; documentId: string }, unknown>(
    workspaceId,
    boardId,
    ({ taskId, documentId }) => taskApi.linkDocument(workspaceId, boardId, taskId, documentId),
  )
}

export function useUnlinkTaskDocument(workspaceId: string, boardId: string) {
  return useTaskMutation<{ taskId: string; documentId: string }, unknown>(
    workspaceId,
    boardId,
    ({ taskId, documentId }) => taskApi.unlinkDocument(workspaceId, boardId, taskId, documentId),
  )
}

/**
 * Mirrors the backend's ordering rules locally for the optimistic update:
 * remove the task from its column, insert it at the target index, and renumber
 * both affected columns so positions stay contiguous.
 */
export function applyMoveLocally(board: Board, taskId: string, move: MoveTaskPayload): Board {
  const task = board.columns.flatMap((column) => column.tasks).find((item) => item.id === taskId)
  if (!task) {
    return board
  }

  const columns = board.columns.map((column) => ({
    ...column,
    tasks: column.tasks.filter((item) => item.id !== taskId),
  }))

  const target = columns.find((column) => column.id === move.columnId)
  if (!target) {
    return board
  }

  const index = Math.max(0, Math.min(move.position, target.tasks.length))
  target.tasks.splice(index, 0, { ...task, columnId: move.columnId })

  return {
    ...board,
    columns: columns.map((column) => ({
      ...column,
      tasks: column.tasks.map((item, position) => ({ ...item, position })),
    })),
  }
}
