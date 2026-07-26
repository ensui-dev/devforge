/**
 * A logo lives in the instance row rather than in object storage, so a self-hosted
 * DevForge needs no file server and a backup is one `pg_dump`. The size limit is
 * what keeps that honest.
 */
export const MAX_LOGO_BYTES = 64 * 1024

const ALLOWED = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp']

/**
 * Reads an image file as a data URI.
 *
 * @throws Error with a message meant for the operator, not a stack trace
 */
export async function readLogoImage(file: File): Promise<string> {
  if (!ALLOWED.includes(file.type)) {
    throw new Error('Use a PNG, JPEG, SVG, or WebP image.')
  }
  if (file.size > MAX_LOGO_BYTES) {
    throw new Error(
      `That image is ${Math.round(file.size / 1024)}KB. Keep it under ${MAX_LOGO_BYTES / 1024}KB.`,
    )
  }

  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('Could not read that file.'))
    reader.onload = () => {
      const result = reader.result
      if (typeof result !== 'string') {
        reject(new Error('Could not read that file.'))
        return
      }
      resolve(result)
    }
    reader.readAsDataURL(file)
  })
}
