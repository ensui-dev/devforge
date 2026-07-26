import type { AuditAction, AuditChange, AuditEvent } from '../../shared/types'

/**
 * Turns a log row into a sentence.
 *
 * The stored event is deliberately machine-shaped — an action enum, a target, and
 * a small detail object — because that is what stays queryable. Rendering it as
 * prose belongs here rather than in the database, so the wording can change
 * without a migration and without rewriting history.
 */

/** Verb phrases, past tense, with the target supplied separately. */
const PHRASES: Record<AuditAction, string> = {
  WORKSPACE_CREATED: 'created the workspace',
  WORKSPACE_UPDATED: 'changed the workspace',
  WORKSPACE_DELETED: 'deleted the workspace',
  WORKSPACE_PUBLISHED: 'published the documentation',
  WORKSPACE_UNPUBLISHED: 'took the documentation offline',

  MEMBER_ADDED: 'added',
  MEMBER_ROLE_CHANGED: 'changed the role of',
  MEMBER_REMOVED: 'removed',

  DOCUMENT_CREATED: 'created',
  DOCUMENT_UPDATED: 'edited',
  DOCUMENT_DELETED: 'deleted',
  DOCUMENT_RESTORED: 'restored',
  DOCUMENT_LINKED: 'linked',
  DOCUMENT_UNLINKED: 'unlinked',

  BOARD_CREATED: 'created the board',
  BOARD_UPDATED: 'renamed the board',
  BOARD_DELETED: 'deleted the board',
  COLUMN_CREATED: 'added the column',
  COLUMN_UPDATED: 'changed the column',
  COLUMN_DELETED: 'removed the column',

  TASK_CREATED: 'created the task',
  TASK_UPDATED: 'changed the task',
  TASK_MOVED: 'moved the task',
  TASK_DELETED: 'deleted the task',
  TASK_DOCUMENT_LINKED: 'cited a document from',
  TASK_DOCUMENT_UNLINKED: 'stopped citing a document from',

  INSTANCE_SET_UP: 'set up this instance',
  INSTANCE_SETTINGS_CHANGED: 'changed the instance settings',
  INSTANCE_ADMIN_GRANTED: 'made an instance administrator of',
  INSTANCE_ADMIN_REVOKED: 'removed instance administration from',
  ACCOUNT_CREATED: 'created the account',
}

/** Actions whose target is already named in the phrase. */
const SELF_DESCRIBING: ReadonlySet<AuditAction> = new Set<AuditAction>([
  'WORKSPACE_CREATED',
  'WORKSPACE_UPDATED',
  'WORKSPACE_DELETED',
  'WORKSPACE_PUBLISHED',
  'WORKSPACE_UNPUBLISHED',
  'INSTANCE_SET_UP',
  'INSTANCE_SETTINGS_CHANGED',
])

export function describeAction(action: AuditAction): string {
  return PHRASES[action] ?? action.toLowerCase().replace(/_/g, ' ')
}

/** Who did it. Null actors are real: first-run setup precedes every account. */
export function describeActor(event: AuditEvent): string {
  if (!event.actorLabel) {
    return 'Setup'
  }
  // Stored as `Name <email>`; the name alone reads better in a feed.
  const match = /^(.*?)\s*<.*>$/.exec(event.actorLabel)
  return match ? match[1] : event.actorLabel
}

/** The email, for the tooltip — two people can share a display name. */
export function describeActorDetail(event: AuditEvent): string | undefined {
  return event.actorLabel ?? undefined
}

export function describeTarget(event: AuditEvent): string | null {
  if (SELF_DESCRIBING.has(event.action)) {
    return null
  }
  return event.targetLabel
}

function isChange(value: unknown): value is AuditChange {
  return typeof value === 'object' && value !== null && 'from' in value && 'to' in value
}

/** Human-readable field names; anything unlisted falls back to the key. */
const FIELD_LABELS: Record<string, string> = {
  documentType: 'type',
  contentLength: 'length',
  wipLimit: 'WIP limit',
  publicDocsEnabled: 'public documentation',
  allowedEmailDomains: 'allowed domains',
  registrationMode: 'registration',
  handbookPath: 'handbook path',
  restoredFrom: 'restored from',
  linkedDocuments: 'linked documents',
  targetDocumentId: 'document',
  referenceType: 'relationship',
}

function label(key: string): string {
  return FIELD_LABELS[key] ?? key.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
}

/**
 * The specifics, one short phrase per entry.
 *
 * Fields that moved read as `type: GENERAL → ARCHITECTURE`; plain values read as
 * `role: ADMIN`. Both are kept short enough to sit on one line of a feed.
 */
export function describeDetail(event: AuditEvent): string[] {
  return Object.entries(event.detail ?? {}).map(([key, value]) => {
    if (isChange(value)) {
      return `${label(key)}: ${value.from} → ${value.to}`
    }
    if (typeof value === 'boolean') {
      // `voluntary: true` on a removal means someone left rather than was removed.
      return value ? label(key) : `not ${label(key)}`
    }
    return `${label(key)}: ${String(value)}`
  })
}
