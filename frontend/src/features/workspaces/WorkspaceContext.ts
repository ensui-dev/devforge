import { createContext, useContext } from 'react'
import type { Workspace } from '../../shared/types'

/**
 * Makes the resolved workspace — and therefore the caller's role — available to
 * every screen inside it, so no child refetches it merely to decide whether to
 * show a write action.
 */
export const WorkspaceContext = createContext<Workspace | null>(null)

export function useCurrentWorkspace(): Workspace {
  const workspace = useContext(WorkspaceContext)
  if (!workspace) {
    throw new Error('useCurrentWorkspace must be used inside a WorkspaceLayout route')
  }
  return workspace
}
