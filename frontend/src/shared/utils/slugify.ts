/**
 * Derives a URL slug from a title, matching the backend's accepted pattern
 * (`^[a-z0-9]+(?:-[a-z0-9]+)*$`) so a suggested slug never fails validation.
 */
export function slugify(value: string): string {
  return value
    .normalize('NFKD')
    // Strip diacritics so "Café" becomes "cafe" rather than losing the letter.
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 100)
    // A trailing hyphen can survive the slice.
    .replace(/-+$/, '')
}

/** Formats an ISO timestamp for display, e.g. "12 Mar 2026". */
export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

/** Relative time for recency cues, falling back to a date once it is stale. */
export function formatRelative(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const seconds = Math.round((Date.now() - date.getTime()) / 1000)
  if (seconds < 60) {
    return 'just now'
  }
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) {
    return `${minutes}m ago`
  }
  const hours = Math.round(minutes / 60)
  if (hours < 24) {
    return `${hours}h ago`
  }
  const days = Math.round(hours / 24)
  if (days < 30) {
    return `${days}d ago`
  }
  return formatDate(iso)
}

/** First letters of a name, for avatar chips. */
export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}
