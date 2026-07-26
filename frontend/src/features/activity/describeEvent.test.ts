import { describe, expect, it } from 'vitest'
import {
  describeAction,
  describeActor,
  describeDetail,
  describeTarget,
} from './describeEvent'
import type { AuditEvent } from '../../shared/types'

function event(overrides: Partial<AuditEvent> = {}): AuditEvent {
  return {
    id: 'e1',
    occurredAt: '2026-07-27T10:00:00Z',
    actorId: 'u1',
    actorLabel: 'Ada Lovelace <ada@example.com>',
    action: 'DOCUMENT_UPDATED',
    targetType: 'DOCUMENT',
    targetId: 'd1',
    targetLabel: 'Event ingestion',
    workspaceId: 'w1',
    detail: {},
    ...overrides,
  }
}

describe('describeEvent', () => {
  it('renders an entry as a sentence', () => {
    const e = event()
    expect(`${describeActor(e)} ${describeAction(e.action)} ${describeTarget(e)}`).toBe(
      'Ada Lovelace edited Event ingestion',
    )
  })

  /** The name reads better in a feed; the address belongs in the tooltip. */
  it('shows the display name rather than the stored name-and-address', () => {
    expect(describeActor(event())).toBe('Ada Lovelace')
  })

  it('copes with an actor label that is only an address', () => {
    expect(describeActor(event({ actorLabel: 'ada@example.com' }))).toBe('ada@example.com')
  })

  /** First-run setup precedes every account, so a null actor is real, not missing data. */
  it('names the actorless case rather than rendering a blank', () => {
    expect(describeActor(event({ actorLabel: null, actorId: null }))).toBe('Setup')
  })

  /** "created the workspace Platform Platform" would be silly. */
  it('does not repeat a target already named in the phrase', () => {
    expect(describeTarget(event({ action: 'WORKSPACE_CREATED', targetLabel: 'Platform' }))).toBeNull()
    expect(describeAction('WORKSPACE_CREATED')).toBe('created the workspace')
  })

  it('keeps the target for actions whose phrase needs one', () => {
    expect(describeTarget(event({ action: 'MEMBER_ADDED', targetLabel: 'dev@acme.test' }))).toBe(
      'dev@acme.test',
    )
  })

  it('renders a changed field as from → to', () => {
    const detail = describeDetail(
      event({ detail: { title: { from: 'Design', to: 'Renamed' } } }),
    )
    expect(detail).toEqual(['title: Design → Renamed'])
  })

  it('uses readable names for fields the API spells in camelCase', () => {
    expect(describeDetail(event({ detail: { documentType: { from: 'GENERAL', to: 'API' } } })))
      .toEqual(['type: GENERAL → API'])
    expect(describeDetail(event({ detail: { wipLimit: 3 } }))).toEqual(['WIP limit: 3'])
    expect(describeDetail(event({ detail: { publicDocsEnabled: { from: 'true', to: 'false' } } })))
      .toEqual(['public documentation: true → false'])
  })

  it('renders a plain value without an arrow', () => {
    expect(describeDetail(event({ detail: { role: 'ADMIN' } }))).toEqual(['role: ADMIN'])
  })

  /** `voluntary` distinguishes leaving from being removed, which matters. */
  it('renders booleans as a phrase rather than true/false', () => {
    expect(describeDetail(event({ detail: { voluntary: true } }))).toEqual(['voluntary'])
    expect(describeDetail(event({ detail: { voluntary: false } }))).toEqual(['not voluntary'])
  })

  it('renders an empty detail as nothing at all', () => {
    expect(describeDetail(event())).toEqual([])
  })

  /**
   * The backend enum can grow without the client being redeployed, so an unknown
   * action must still read as something rather than crashing or rendering blank.
   */
  it('falls back readably for an action it does not know', () => {
    // @ts-expect-error deliberately outside the union, simulating a newer server
    expect(describeAction('SOMETHING_NEW_HAPPENED')).toBe('something new happened')
  })
})
