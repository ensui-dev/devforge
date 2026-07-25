import { describe, expect, it } from 'vitest'
import { roleAtLeast } from './index'

/**
 * Mirrors the backend's WorkspaceRoleTest. The UI hides actions using this
 * function, so a divergence would show controls the API then rejects.
 */
describe('roleAtLeast', () => {
  it('grants an owner every capability', () => {
    expect(roleAtLeast('OWNER', 'OWNER')).toBe(true)
    expect(roleAtLeast('OWNER', 'ADMIN')).toBe(true)
    expect(roleAtLeast('OWNER', 'MEMBER')).toBe(true)
    expect(roleAtLeast('OWNER', 'VIEWER')).toBe(true)
  })

  it('limits a viewer to reading', () => {
    expect(roleAtLeast('VIEWER', 'VIEWER')).toBe(true)
    expect(roleAtLeast('VIEWER', 'MEMBER')).toBe(false)
    expect(roleAtLeast('VIEWER', 'ADMIN')).toBe(false)
  })

  it('lets a member write but not administer', () => {
    expect(roleAtLeast('MEMBER', 'MEMBER')).toBe(true)
    expect(roleAtLeast('MEMBER', 'ADMIN')).toBe(false)
  })

  it('lets an admin administer but not own', () => {
    expect(roleAtLeast('ADMIN', 'ADMIN')).toBe(true)
    expect(roleAtLeast('ADMIN', 'OWNER')).toBe(false)
  })

  it('treats a missing role as no access', () => {
    expect(roleAtLeast(null, 'VIEWER')).toBe(false)
    expect(roleAtLeast(undefined, 'VIEWER')).toBe(false)
  })
})
