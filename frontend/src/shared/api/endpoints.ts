import { apiRequest, queryString } from './client'
import type {
  AdminInstance,
  AuditAction,
  AuditEvent,
  AuthenticationResult,
  Board,
  BoardSummary,
  ColumnPayload,
  CreateAccountPayload,
  CreateTaskPayload,
  DocumentDetail,
  DocumentPayload,
  DocumentReference,
  DocumentRevision,
  DocumentSummary,
  DocumentType,
  GitAccessToken,
  GitAccessTokenPayload,
  GitRepository,
  Handbook,
  Instance,
  InstanceSettingsPayload,
  IssuedGitAccessToken,
  InstanceUser,
  LoginPayload,
  MoveTaskPayload,
  OwnerDocs,
  Page,
  Publication,
  PublicDocument,
  PublishedWorkspace,
  ReferenceType,
  RegisterPayload,
  SetupPayload,
  SyncSettings,
  SyncSettingsPayload,
  SetupResult,
  Task,
  UpdateTaskPayload,
  User,
  Workspace,
  WorkspaceMember,
  WorkspacePayload,
  WorkspaceRole,
} from '../types'

/** Every endpoint the client uses, grouped by resource. */

const json = (body: unknown): RequestInit => ({ body: JSON.stringify(body) })

export const authApi = {
  register: (payload: RegisterPayload) =>
    apiRequest<AuthenticationResult>('/api/auth/register', { method: 'POST', ...json(payload) }),
  login: (payload: LoginPayload) =>
    apiRequest<AuthenticationResult>('/api/auth/login', { method: 'POST', ...json(payload) }),
  me: () => apiRequest<User>('/api/auth/me'),
}

export const userApi = {
  search: (query: string) => apiRequest<User[]>(`/api/users${queryString({ q: query })}`),
}

export const workspaceApi = {
  list: () => apiRequest<Workspace[]>('/api/workspaces'),
  get: (workspaceId: string) => apiRequest<Workspace>(`/api/workspaces/${workspaceId}`),
  create: (payload: WorkspacePayload) =>
    apiRequest<Workspace>('/api/workspaces', { method: 'POST', ...json(payload) }),
  update: (workspaceId: string, payload: WorkspacePayload) =>
    apiRequest<Workspace>(`/api/workspaces/${workspaceId}`, { method: 'PUT', ...json(payload) }),
  remove: (workspaceId: string) =>
    apiRequest<void>(`/api/workspaces/${workspaceId}`, { method: 'DELETE' }),
}

export const memberApi = {
  list: (workspaceId: string) =>
    apiRequest<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members`),
  add: (workspaceId: string, email: string, role: WorkspaceRole) =>
    apiRequest<WorkspaceMember>(`/api/workspaces/${workspaceId}/members`, {
      method: 'POST',
      ...json({ email, role }),
    }),
  changeRole: (workspaceId: string, userId: string, role: WorkspaceRole) =>
    apiRequest<WorkspaceMember>(`/api/workspaces/${workspaceId}/members/${userId}`, {
      method: 'PUT',
      ...json({ role }),
    }),
  remove: (workspaceId: string, userId: string) =>
    apiRequest<void>(`/api/workspaces/${workspaceId}/members/${userId}`, { method: 'DELETE' }),
}

export const documentApi = {
  list: (
    workspaceId: string,
    options: { documentType?: DocumentType; page?: number; size?: number } = {},
  ) =>
    apiRequest<Page<DocumentSummary>>(
      `/api/workspaces/${workspaceId}/documents${queryString({
        documentType: options.documentType,
        page: options.page,
        size: options.size,
      })}`,
    ),
  search: (workspaceId: string, query: string, options: { page?: number; size?: number } = {}) =>
    apiRequest<Page<DocumentSummary>>(
      `/api/workspaces/${workspaceId}/documents/search${queryString({
        q: query,
        page: options.page,
        size: options.size,
      })}`,
    ),
  get: (workspaceId: string, documentId: string) =>
    apiRequest<DocumentDetail>(`/api/workspaces/${workspaceId}/documents/${documentId}`),
  create: (workspaceId: string, payload: DocumentPayload) =>
    apiRequest<DocumentDetail>(`/api/workspaces/${workspaceId}/documents`, {
      method: 'POST',
      ...json(payload),
    }),
  update: (workspaceId: string, documentId: string, payload: DocumentPayload) =>
    apiRequest<DocumentDetail>(`/api/workspaces/${workspaceId}/documents/${documentId}`, {
      method: 'PUT',
      ...json(payload),
    }),
  remove: (workspaceId: string, documentId: string) =>
    apiRequest<void>(`/api/workspaces/${workspaceId}/documents/${documentId}`, {
      method: 'DELETE',
    }),

  listReferences: (workspaceId: string, documentId: string) =>
    apiRequest<DocumentReference[]>(
      `/api/workspaces/${workspaceId}/documents/${documentId}/references`,
    ),
  addReference: (
    workspaceId: string,
    documentId: string,
    targetDocumentId: string,
    referenceType: ReferenceType,
  ) =>
    apiRequest<DocumentReference>(
      `/api/workspaces/${workspaceId}/documents/${documentId}/references`,
      { method: 'POST', ...json({ targetDocumentId, referenceType }) },
    ),
  removeReference: (workspaceId: string, documentId: string, referenceId: string) =>
    apiRequest<void>(
      `/api/workspaces/${workspaceId}/documents/${documentId}/references/${referenceId}`,
      { method: 'DELETE' },
    ),
}

export const boardApi = {
  list: (workspaceId: string) =>
    apiRequest<BoardSummary[]>(`/api/workspaces/${workspaceId}/boards`),
  get: (workspaceId: string, boardId: string) =>
    apiRequest<Board>(`/api/workspaces/${workspaceId}/boards/${boardId}`),
  create: (workspaceId: string, name: string) =>
    apiRequest<Board>(`/api/workspaces/${workspaceId}/boards`, { method: 'POST', ...json({ name }) }),
  rename: (workspaceId: string, boardId: string, name: string) =>
    apiRequest<Board>(`/api/workspaces/${workspaceId}/boards/${boardId}`, {
      method: 'PUT',
      ...json({ name }),
    }),
  remove: (workspaceId: string, boardId: string) =>
    apiRequest<void>(`/api/workspaces/${workspaceId}/boards/${boardId}`, { method: 'DELETE' }),

  addColumn: (workspaceId: string, boardId: string, payload: ColumnPayload) =>
    apiRequest<Board>(`/api/workspaces/${workspaceId}/boards/${boardId}/columns`, {
      method: 'POST',
      ...json(payload),
    }),
  updateColumn: (workspaceId: string, boardId: string, columnId: string, payload: ColumnPayload) =>
    apiRequest<Board>(`/api/workspaces/${workspaceId}/boards/${boardId}/columns/${columnId}`, {
      method: 'PUT',
      ...json(payload),
    }),
  moveColumn: (workspaceId: string, boardId: string, columnId: string, position: number) =>
    apiRequest<Board>(
      `/api/workspaces/${workspaceId}/boards/${boardId}/columns/${columnId}/position`,
      { method: 'PATCH', ...json({ position }) },
    ),
  removeColumn: (workspaceId: string, boardId: string, columnId: string) =>
    apiRequest<Board>(`/api/workspaces/${workspaceId}/boards/${boardId}/columns/${columnId}`, {
      method: 'DELETE',
    }),
}

export const taskApi = {
  create: (workspaceId: string, boardId: string, payload: CreateTaskPayload) =>
    apiRequest<Task>(`/api/workspaces/${workspaceId}/boards/${boardId}/tasks`, {
      method: 'POST',
      ...json(payload),
    }),
  update: (workspaceId: string, boardId: string, taskId: string, payload: UpdateTaskPayload) =>
    apiRequest<Task>(`/api/workspaces/${workspaceId}/boards/${boardId}/tasks/${taskId}`, {
      method: 'PUT',
      ...json(payload),
    }),
  move: (workspaceId: string, boardId: string, taskId: string, payload: MoveTaskPayload) =>
    apiRequest<Task>(`/api/workspaces/${workspaceId}/boards/${boardId}/tasks/${taskId}/position`, {
      method: 'PATCH',
      ...json(payload),
    }),
  remove: (workspaceId: string, boardId: string, taskId: string) =>
    apiRequest<void>(`/api/workspaces/${workspaceId}/boards/${boardId}/tasks/${taskId}`, {
      method: 'DELETE',
    }),
  linkDocument: (workspaceId: string, boardId: string, taskId: string, documentId: string) =>
    apiRequest<Task>(`/api/workspaces/${workspaceId}/boards/${boardId}/tasks/${taskId}/documents`, {
      method: 'POST',
      ...json({ documentId }),
    }),
  unlinkDocument: (workspaceId: string, boardId: string, taskId: string, documentId: string) =>
    apiRequest<Task>(
      `/api/workspaces/${workspaceId}/boards/${boardId}/tasks/${taskId}/documents/${documentId}`,
      { method: 'DELETE' },
    ),
}

/**
 * The product's own documentation. These endpoints need no session, so the docs
 * are readable before anyone signs up.
 */
export const handbookApi = {
  directory: () => apiRequest<PublishedWorkspace[]>('/api/public/docs'),
  byOwner: (handle: string) => apiRequest<OwnerDocs>(`/api/public/docs/${handle}`),
  contents: (handle: string, workspaceSlug: string) =>
    apiRequest<Handbook>(`/api/public/docs/${handle}/${workspaceSlug}`),
  page: (handle: string, workspaceSlug: string, documentSlug: string) =>
    apiRequest<PublicDocument>(`/api/public/docs/${handle}/${workspaceSlug}/${documentSlug}`),
}

/**
 * A document's history. Bodies are omitted from the list and present on a single
 * revision, so a history panel costs one small request rather than the whole
 * document once per revision.
 */
export const revisionApi = {
  list: (workspaceId: string, documentId: string, options: { page?: number; size?: number } = {}) =>
    apiRequest<Page<DocumentRevision>>(
      `/api/workspaces/${workspaceId}/documents/${documentId}/revisions${queryString(options)}`,
    ),
  get: (workspaceId: string, documentId: string, revision: number) =>
    apiRequest<DocumentRevision>(
      `/api/workspaces/${workspaceId}/documents/${documentId}/revisions/${revision}`,
    ),
  restore: (workspaceId: string, documentId: string, revision: number) =>
    apiRequest<DocumentDetail>(
      `/api/workspaces/${workspaceId}/documents/${documentId}/revisions/${revision}/restore`,
      { method: 'POST' },
    ),
}

/** Who changed what. */
export const activityApi = {
  forWorkspace: (
    workspaceId: string,
    options: { action?: AuditAction; page?: number; size?: number } = {},
  ) => apiRequest<Page<AuditEvent>>(`/api/workspaces/${workspaceId}/activity${queryString(options)}`),
  forInstance: (options: { action?: AuditAction; page?: number; size?: number } = {}) =>
    apiRequest<Page<AuditEvent>>(`/api/instance/activity${queryString(options)}`),
}

export const publicationApi = {
  get: (workspaceId: string) =>
    apiRequest<Publication>(`/api/workspaces/${workspaceId}/publication`),
  set: (workspaceId: string, published: boolean) =>
    apiRequest<Publication>(`/api/workspaces/${workspaceId}/publication`, {
      method: 'PUT',
      ...json({ published }),
    }),
}

/**
 * This deployment: how it is branded, whether it has been set up, and the
 * settings its operator controls.
 *
 * {@link describe} needs no session — the client cannot render its own header
 * before anyone signs in, and the setup screen has to be reachable on an instance
 * that has no accounts at all.
 */
export const instanceApi = {
  describe: () => apiRequest<Instance>('/api/public/instance'),
  setUp: (payload: SetupPayload) =>
    apiRequest<SetupResult>('/api/setup', { method: 'POST', ...json(payload) }),
  settings: () => apiRequest<AdminInstance>('/api/instance'),
  update: (payload: InstanceSettingsPayload) =>
    apiRequest<AdminInstance>('/api/instance', { method: 'PUT', ...json(payload) }),
  /** How people are added to an instance that does not accept registrations. */
  createAccount: (payload: CreateAccountPayload) =>
    apiRequest<InstanceUser>('/api/instance/users', { method: 'POST', ...json(payload) }),
  administrators: () => apiRequest<InstanceUser[]>('/api/instance/admins'),
  setInstanceAdmin: (userId: string, instanceAdmin: boolean) =>
    apiRequest<InstanceUser>(`/api/instance/users/${userId}/admin`, {
      method: 'PUT',
      ...json({ instanceAdmin }),
    }),
}

/**
 * A workspace's git connection. Admin-only, because the response says whether
 * credentials are stored and exposes the webhook URL.
 */
export const syncApi = {
  get: (workspaceId: string) => apiRequest<SyncSettings>(`/api/workspaces/${workspaceId}/sync`),
  save: (workspaceId: string, payload: SyncSettingsPayload) =>
    apiRequest<SyncSettings>(`/api/workspaces/${workspaceId}/sync`, {
      method: 'PUT',
      ...json(payload),
    }),
  /** Runs a sync immediately, so settings can be checked without pushing. */
  run: (workspaceId: string) =>
    apiRequest<SyncSettings>(`/api/workspaces/${workspaceId}/sync/run`, { method: 'POST' }),
  /** Returns the new secret in the clear, once. It is stored encrypted. */
  generateSecret: (workspaceId: string) =>
    apiRequest<{ webhookSecret: string }>(`/api/workspaces/${workspaceId}/sync/secret`, {
      method: 'POST',
    }),
  rotateUrl: (workspaceId: string) =>
    apiRequest<SyncSettings>(`/api/workspaces/${workspaceId}/sync/rotate-url`, { method: 'POST' }),
  disconnect: (workspaceId: string) =>
    apiRequest<void>(`/api/workspaces/${workspaceId}/sync`, { method: 'DELETE' }),
  /** The repository DevForge hosts, for the clone URL. Readable by any member. */
  repository: (workspaceId: string) =>
    apiRequest<GitRepository>(`/api/workspaces/${workspaceId}/git`),
}

/**
 * The signed-in account's git credentials.
 *
 * Scoped to the caller by the server: there is no path here that names a user, so
 * there is no way to reach anyone else's.
 */
export const gitTokenApi = {
  list: () => apiRequest<GitAccessToken[]>('/api/me/git-tokens'),
  /** The response carries the secret. It is the only time it can be read. */
  create: (payload: GitAccessTokenPayload) =>
    apiRequest<IssuedGitAccessToken>('/api/me/git-tokens', { method: 'POST', ...json(payload) }),
  revoke: (tokenId: string) =>
    apiRequest<void>(`/api/me/git-tokens/${tokenId}`, { method: 'DELETE' }),
}
