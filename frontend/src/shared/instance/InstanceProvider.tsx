import { useEffect, useMemo } from 'react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { instanceApi } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import { InstanceContext, FALLBACK_INSTANCE } from './InstanceContext'
import type { InstanceContextValue } from './InstanceContext'
import type { Instance } from '../types'

/** Six-digit hex, the one form the accent may take. */
const HEX = /^#[0-9a-fA-F]{6}$/

/**
 * Repaints the accent from the operator's chosen colour.
 *
 * Only `--trace` and the two tones derived from it move; everything else in the
 * design system stays put. That is what keeps a rebrand from turning into a
 * redesign — the operator picks one colour, and contrast, spacing, and the
 * signal palette are unaffected.
 */
function applyAccent(accent: string | null) {
  const root = document.documentElement
  if (!accent || !HEX.test(accent)) {
    root.style.removeProperty('--trace')
    root.style.removeProperty('--trace-strong')
    root.style.removeProperty('--trace-wash')
    return
  }
  root.style.setProperty('--trace', accent)
  root.style.setProperty('--trace-strong', `color-mix(in srgb, ${accent} 82%, black)`)
  root.style.setProperty('--trace-wash', `color-mix(in srgb, ${accent} 12%, white)`)
}

/** The mark shipped in `index.html`, used by an instance that has not rebranded. */
const DEFAULT_MARK = '⌁'

/**
 * Points the browser tab at this instance's own mark.
 *
 * An instance that has not rebranded keeps the static `/favicon.svg`, which is
 * DevForge's own mark. One that has picked a glyph, an image, or an accent gets
 * its own — self-hosted software should look like the deployment it is, not like
 * the project it came from.
 */
function applyFavicon(instance: Instance) {
  const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    return
  }

  const mark = instance.logoMark?.trim() || DEFAULT_MARK
  const accent = instance.accentColor && HEX.test(instance.accentColor) ? instance.accentColor : null
  const rebranded = Boolean(instance.logoImage) || mark !== DEFAULT_MARK || accent !== null

  if (!rebranded) {
    link.setAttribute('href', '/favicon.svg')
    link.setAttribute('type', 'image/svg+xml')
    return
  }

  if (instance.logoImage) {
    link.setAttribute('href', instance.logoImage)
    link.removeAttribute('type')
    return
  }

  // The glyph is operator-supplied, so it is escaped before going into markup.
  const glyph = mark.replace(/[<>&"']/g, (c) => `&#${c.charCodeAt(0)};`)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">` +
    `<rect width="32" height="32" rx="7" fill="${accent ?? '#0e6b73'}"/>` +
    `<text x="16" y="16" fill="#f4f6f3" font-size="19" font-family="system-ui,sans-serif" ` +
    `text-anchor="middle" dominant-baseline="central">${glyph}</text></svg>`
  link.setAttribute('href', `data:image/svg+xml,${encodeURIComponent(svg)}`)
  link.setAttribute('type', 'image/svg+xml')
}

/**
 * Loads this deployment's identity once and hands it to the whole app.
 *
 * Kept out of {@link AuthProvider} on purpose: the instance answers before anyone
 * signs in, because the sign-in screen itself needs the name and the setup screen
 * has to be reachable on an instance with no accounts at all.
 */
export function InstanceProvider({ children }: { children: ReactNode }) {
  const { data, isLoading } = useQuery({
    queryKey: queryKeys.instance.public,
    queryFn: instanceApi.describe,
    // Branding changes rarely, and every page reads this.
    staleTime: 5 * 60_000,
  })

  const instance = data ?? FALLBACK_INSTANCE

  useEffect(() => {
    applyAccent(instance.accentColor)
  }, [instance.accentColor])

  useEffect(() => {
    document.title = instance.tagline ? `${instance.name} — ${instance.tagline}` : instance.name
  }, [instance.name, instance.tagline])

  useEffect(() => {
    applyFavicon(instance)
  }, [instance])

  const value = useMemo<InstanceContextValue>(
    () => ({
      instance,
      isLoading,
      // The handbook path is a `handle/slug` pair; without one, /docs falls back
      // to the directory of everything this instance publishes.
      docsPath: (slug?: string) => {
        if (!instance.handbookPath) {
          return '/docs'
        }
        return slug ? `/docs/${instance.handbookPath}/${slug}` : `/docs/${instance.handbookPath}`
      },
    }),
    [instance, isLoading],
  )

  return <InstanceContext.Provider value={value}>{children}</InstanceContext.Provider>
}
