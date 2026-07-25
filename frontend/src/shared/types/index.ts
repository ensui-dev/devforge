/** Mirrors the backend's published contract types. */

export type DocumentType =
  | 'GENERAL'
  | 'CODE'
  | 'PROCEDURE'
  | 'TECHNOLOGY'
  | 'TECH_STACK'
  | 'ARCHITECTURE'
  | 'API'
  | 'RUNBOOK'
  | 'DECISION'

export type ReferenceType = 'RELATED' | 'DEPENDS_ON' | 'IMPLEMENTS' | 'DOCUMENTS' | 'SUPERSEDES'

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type WorkspaceRole = 'VIEWER' | 'MEMBER' | 'ADMIN' | 'OWNER'

/** Ranked to match the backend, so the UI can hide actions it cannot perform. */
const ROLE_RANK: Record<WorkspaceRole, number> = {
  VIEWER: 0,
  MEMBER: 1,
  ADMIN: 2,
  OWNER: 3,
}

export function roleAtLeast(role: WorkspaceRole | null | undefined, required: WorkspaceRole): boolean {
  if (!role) {
    return false
  }
  return ROLE_RANK[role] >= ROLE_RANK[required]
}

export interface User {
  id: string
  email: string
  displayName: string
}

export interface AuthenticationResult {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

export interface Workspace {
  id: string
  name: string
  description: string | null
  slug: string
  callerRole: WorkspaceRole
  createdAt: string
  updatedAt: string
}

export interface WorkspaceMember {
  userId: string
  email: string | null
  displayName: string
  role: WorkspaceRole
  joinedAt: string
}

export interface DocumentSummary {
  id: string
  workspaceId: string
  title: string
  slug: string
  excerpt: string
  documentType: DocumentType
  createdAt: string
  updatedAt: string
}

export interface DocumentDetail {
  id: string
  workspaceId: string
  title: string
  slug: string
  content: string
  documentType: DocumentType
  createdAt: string
  updatedAt: string
}

export interface DocumentReference {
  id: string
  referenceType: ReferenceType
  /** True when the viewed document declared this link; false for a backlink. */
  outgoing: boolean
  relatedDocumentId: string
  relatedDocumentTitle: string | null
  relatedDocumentSlug: string | null
  relatedDocumentType: DocumentType | null
  createdAt: string
}

export interface TaskAssignee {
  id: string
  displayName: string
  email: string
}

export interface LinkedDocument {
  id: string
  title: string
  slug: string
  documentType: DocumentType
}

export interface Task {
  id: string
  boardId: string
  columnId: string
  title: string
  description: string | null
  position: number
  priority: TaskPriority
  assignee: TaskAssignee | null
  linkedDocuments: LinkedDocument[]
  createdAt: string
  updatedAt: string
}

export interface BoardColumn {
  id: string
  name: string
  position: number
  wipLimit: number | null
  tasks: Task[]
}

export interface Board {
  id: string
  workspaceId: string
  name: string
  columns: BoardColumn[]
  createdAt: string
  updatedAt: string
}

export interface BoardSummary {
  id: string
  workspaceId: string
  name: string
  columnCount: number
  taskCount: number
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

/* Request payloads */

export interface RegisterPayload {
  email: string
  displayName: string
  password: string
}

export interface LoginPayload {
  email: string
  password: string
}

export interface WorkspacePayload {
  name: string
  description?: string
  slug: string
}

export interface DocumentPayload {
  title: string
  slug: string
  content?: string
  documentType: DocumentType
}

export interface CreateTaskPayload {
  title: string
  description?: string
  columnId: string
  priority?: TaskPriority
  assigneeId?: string | null
  linkedDocumentIds?: string[]
}

export interface UpdateTaskPayload {
  title: string
  description?: string
  priority?: TaskPriority
  assigneeId?: string | null
}

export interface MoveTaskPayload {
  columnId: string
  position: number
}

export interface ColumnPayload {
  name: string
  wipLimit?: number | null
}

/* Display metadata. Kept beside the types so labels stay consistent everywhere. */

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  GENERAL: 'General',
  CODE: 'Code',
  PROCEDURE: 'Procedure',
  TECHNOLOGY: 'Technology',
  TECH_STACK: 'Tech stack',
  ARCHITECTURE: 'Architecture',
  API: 'API',
  RUNBOOK: 'Runbook',
  DECISION: 'Decision',
}

export const REFERENCE_TYPE_LABELS: Record<ReferenceType, string> = {
  RELATED: 'Related to',
  DEPENDS_ON: 'Depends on',
  IMPLEMENTS: 'Implements',
  DOCUMENTS: 'Documents',
  SUPERSEDES: 'Supersedes',
}

/** How a backlink reads from the far end — the inverse of the outgoing phrasing. */
export const REFERENCE_TYPE_INVERSE_LABELS: Record<ReferenceType, string> = {
  RELATED: 'Related from',
  DEPENDS_ON: 'Required by',
  IMPLEMENTS: 'Implemented by',
  DOCUMENTS: 'Documented by',
  SUPERSEDES: 'Superseded by',
}

export const TASK_PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export const WORKSPACE_ROLES: WorkspaceRole[] = ['VIEWER', 'MEMBER', 'ADMIN', 'OWNER']

export const ROLE_DESCRIPTIONS: Record<WorkspaceRole, string> = {
  VIEWER: 'Reads documents and boards',
  MEMBER: 'Writes documents, boards, and tasks',
  ADMIN: 'Manages the team and deletes boards',
  OWNER: 'Full control, including deleting the workspace',
}
