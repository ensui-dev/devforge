import { createContext } from 'react'
import type { Instance } from '../types'

export interface InstanceContextValue {
  instance: Instance
  /** True until the first fetch settles; branding is provisional until then. */
  isLoading: boolean
  /** Builds a link into this instance's default documentation. */
  docsPath: (slug?: string) => string
}

/**
 * DevForge is self-hosted, so the product's own name, mark, and policy are
 * per-deployment data rather than constants in the bundle.
 */
export const InstanceContext = createContext<InstanceContextValue | null>(null)

/**
 * What the client shows before the instance has answered, and if it never does.
 *
 * An unreachable API must not blank the header — the surrounding page still
 * renders, with the project's own name, and the setup gate stays shut because
 * {@code configured} is not asserted either way from a failed request.
 */
export const FALLBACK_INSTANCE: Instance = {
  configured: true,
  name: 'DevForge',
  tagline: null,
  logoMark: '⌁',
  logoImage: null,
  accentColor: null,
  registrationMode: 'OPEN',
  allowedEmailDomains: [],
  publicDocsEnabled: true,
  handbookPath: null,
}
