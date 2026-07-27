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
  /** URL-safe name that namespaces the workspaces this user owns. */
  handle: string
  /** Whether this account may configure the instance. Absent for other people. */
  instanceAdmin?: boolean
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
  /** Held back from the public site even when the workspace is published. */
  internal: boolean
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
  internal: boolean
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
  internal?: boolean
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

/* Public documentation — the handbook workspace, served without a session. */

export interface HandbookEntry {
  id: string
  title: string
  slug: string
  documentType: DocumentType
}

export interface Handbook {
  name: string
  slug: string
  /** Namespaces the slug: this documentation lives at /docs/{ownerHandle}/{slug}. */
  ownerHandle: string
  description: string | null
  entries: HandbookEntry[]
}

/** One entry in the public documentation directory. */
export interface PublishedWorkspace {
  name: string
  slug: string
  ownerHandle: string
  /** Canonical address: /docs/{ownerHandle}/{slug}. */
  publicPath: string
  description: string | null
  pageCount: number
  publishedAt: string
}

/** Everything one owner has published. */
export interface OwnerDocs {
  handle: string
  workspaces: PublishedWorkspace[]
  /**
   * Where a legacy /docs/{slug} link now lives, when this segment is not a handle
   * but does resolve to exactly one published workspace.
   */
  movedTo: string | null
}

/** Whether a workspace's documentation is public, and what that exposes. */
export interface Publication {
  published: boolean
  publishedAt: string | null
  /** Where the documentation is served, or null while private. */
  publicPath: string | null
  publicPages: number
  internalPages: number
}

export interface PublicDocument {
  id: string
  title: string
  slug: string
  content: string
  documentType: DocumentType
  references: DocumentReference[]
  updatedAt: string
}

/* This deployment. DevForge is self-hostable, so nothing about the instance —
   its name, its mark, whether it accepts registrations — is a constant. */

export type RegistrationMode = 'OPEN' | 'RESTRICTED' | 'CLOSED'

/** What an unauthenticated visitor is told, and all the client needs to brand itself. */
export interface Instance {
  /** False until someone completes first-run setup. */
  configured: boolean
  name: string
  tagline: string | null
  logoMark: string | null
  /** Data URI, when the operator uploaded a mark instead of typing one. */
  logoImage: string | null
  accentColor: string | null
  registrationMode: RegistrationMode
  /** Listed so the sign-up form can explain a refusal before it happens. */
  allowedEmailDomains: string[]
  publicDocsEnabled: boolean
  /** The documentation this instance opens by default, as `handle/slug`. */
  handbookPath: string | null
}

/** The operator's view, which carries settings a visitor has no business seeing. */
export interface AdminInstance {
  instance: Instance
  allowedEmailDomains: string | null
  publicBaseUrl: string | null
  setupCompletedAt: string | null
}

export interface InstanceSettingsPayload {
  name: string
  tagline: string
  logoMark: string
  logoImage: string
  accentColor: string
  registrationMode: RegistrationMode
  allowedEmailDomains: string
  publicDocsEnabled: boolean
  handbookPath: string
  publicBaseUrl: string
}

export interface SetupPayload {
  instance: InstanceSettingsPayload
  admin: {
    email: string
    displayName: string
    password: string
  }
}

export interface SetupResult {
  instance: Instance
  /** Echoed so the sign-in form that follows can be prefilled. */
  adminEmail: string
}

/** An account as the operator sees it. */
export interface InstanceUser {
  id: string
  email: string
  displayName: string
  handle: string
  instanceAdmin: boolean
}

export interface CreateAccountPayload {
  email: string
  displayName: string
  password: string
  instanceAdmin: boolean
}

/* Attribution and history. Until these existed a change left only a timestamp
   and a version number behind — you could see that something moved, never who
   moved it or what it said before. */

export type RevisionReason = 'CREATED' | 'UPDATED' | 'RESTORED'

export interface DocumentRevision {
  /** 1-based and contiguous, so it can be named to a reader. */
  revision: number
  title: string
  slug: string
  /** Omitted from list responses; present when a single revision is fetched. */
  content: string | null
  documentType: DocumentType
  internal: boolean
  reason: RevisionReason
  /** Set when a restore produced this revision: the one it was taken from. */
  restoredFrom: number | null
  authorId: string | null
  /**
   * Who wrote it, as they were called at the time. Null for revisions backfilled
   * when history was introduced — nothing recorded an author then.
   */
  authorLabel: string | null
  createdAt: string
}

export type AuditAction =
  | 'WORKSPACE_CREATED' | 'WORKSPACE_UPDATED' | 'WORKSPACE_DELETED'
  | 'WORKSPACE_PUBLISHED' | 'WORKSPACE_UNPUBLISHED'
  | 'MEMBER_ADDED' | 'MEMBER_ROLE_CHANGED' | 'MEMBER_REMOVED'
  | 'DOCUMENT_CREATED' | 'DOCUMENT_UPDATED' | 'DOCUMENT_DELETED'
  | 'DOCUMENT_RESTORED' | 'DOCUMENT_LINKED' | 'DOCUMENT_UNLINKED'
  | 'BOARD_CREATED' | 'BOARD_UPDATED' | 'BOARD_DELETED'
  | 'COLUMN_CREATED' | 'COLUMN_UPDATED' | 'COLUMN_DELETED'
  | 'TASK_CREATED' | 'TASK_UPDATED' | 'TASK_MOVED' | 'TASK_DELETED'
  | 'TASK_DOCUMENT_LINKED' | 'TASK_DOCUMENT_UNLINKED'
  | 'INSTANCE_SET_UP' | 'INSTANCE_SETTINGS_CHANGED'
  | 'INSTANCE_ADMIN_GRANTED' | 'INSTANCE_ADMIN_REVOKED' | 'ACCOUNT_CREATED'

export type AuditTargetType =
  | 'WORKSPACE' | 'MEMBER' | 'DOCUMENT' | 'BOARD' | 'COLUMN' | 'TASK'
  | 'INSTANCE' | 'ACCOUNT'

/** One field that moved, as the log records it. */
export interface AuditChange {
  from: string
  to: string
}

export interface AuditEvent {
  id: string
  occurredAt: string
  actorId: string | null
  /** Null when the event had no signed-in actor, such as first-run setup. */
  actorLabel: string | null
  action: AuditAction
  targetType: AuditTargetType
  targetId: string | null
  targetLabel: string | null
  workspaceId: string | null
  /** Shape varies by action: changed fields, roles, counts. */
  detail: Record<string, AuditChange | string | number | boolean>
}

/* Documentation synced from a git repository. */

export type DeletionPolicy = 'ARCHIVE' | 'DELETE' | 'IGNORE'
export type SyncStatus = 'OK' | 'PARTIAL' | 'FAILED'

export interface SyncSettings {
  configured: boolean
  repositoryUrl: string
  branch: string
  /** Subdirectory holding the markdown; '' means the repository root. */
  documentPath: string
  defaultType: DocumentType
  deletionPolicy: DeletionPolicy
  enabled: boolean
  /**
   * Whether a credential is stored. The values themselves are never returned —
   * echoing a repository token would put it in every cache and log that touched
   * the page, which would defeat encrypting it at rest.
   */
  hasAccessToken: boolean
  hasWebhookSecret: boolean
  webhookUrl: string | null
  webhookId: string | null
  lastAttemptedAt: string | null
  lastSucceededAt: string | null
  lastRef: string | null
  lastStatus: SyncStatus | null
  lastMessage: string | null
  lastCreated: number
  lastUpdated: number
  lastArchived: number
  lastUnchanged: number
  /** Files the last run could not use, when it was only partly applied. */
  problems: string[]
}

/**
 * The git repository DevForge hosts for a workspace.
 *
 * `clonePath` is the path only — the browser supplies the origin, which is the
 * only way to be right about it behind a reverse proxy or a tunnel.
 */
export interface GitRepository {
  /** Whether this instance serves git at all. */
  enabled: boolean
  /** A repository appears the first time somebody clones or pushes. */
  exists: boolean
  clonePath: string | null
  sizeBytes: number | null
}

/** A credential git uses. The secret is never returned after it is issued. */
export interface GitAccessToken {
  id: string
  name: string
  /** The leading characters, enough to tell two tokens apart. */
  hint: string
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string | null
  expired: boolean
}

/** The one response that carries a token's secret. */
export interface IssuedGitAccessToken {
  token: GitAccessToken
  secret: string
}

export interface GitAccessTokenPayload {
  name: string
  /** Omit for a token that works until it is revoked. */
  expiresInDays?: number | null
}

export interface SyncSettingsPayload {
  repositoryUrl: string
  branch: string
  documentPath: string
  defaultType: DocumentType
  deletionPolicy: DeletionPolicy
  enabled: boolean
  /**
   * Omit to leave the stored credential alone; send '' to clear it. A form cannot
   * echo a secret back, so absence is the only way to mean "unchanged".
   */
  accessToken?: string
  webhookSecret?: string
}
