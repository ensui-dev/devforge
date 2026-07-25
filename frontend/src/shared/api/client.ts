/**
 * The single HTTP entry point.
 *
 * Two concerns live here so they cannot be forgotten at a call site: attaching
 * the bearer token, and turning a non-2xx response into a typed {@link ApiError}
 * carrying the backend's field-level messages.
 */

export interface ApiErrorBody {
  status: number
  error: string
  message: string
  path: string
  timestamp: string
  fieldErrors?: Record<string, string>
}

/**
 * Declared with explicit fields rather than TypeScript parameter properties.
 * `erasableSyntaxOnly` is enabled in tsconfig and rejects the shorthand — that is
 * what previously broke `npm run build`.
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }

  /** True when the session is missing or expired. */
  get isUnauthorized(): boolean {
    return this.status === 401
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors[field]
  }
}

type TokenReader = () => string | null

let readToken: TokenReader = () => null
let handleUnauthorized: () => void = () => {}

/**
 * Wires the token source and the expiry handler.
 *
 * Injected by the auth provider so this module stays free of React and can be
 * tested as a plain function.
 */
export function configureApi(options: { getToken: TokenReader; onUnauthorized: () => void }): void {
  readToken = options.getToken
  handleUnauthorized = options.onUnauthorized
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody
    return new ApiError(body.message || response.statusText, response.status, body.fieldErrors ?? {})
  } catch {
    // A proxy or network failure may carry no JSON body.
    return new ApiError(response.statusText || 'Request failed', response.status)
  }
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = readToken()

  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    const error = await toApiError(response)
    if (error.isUnauthorized) {
      // Expired session: cleared once, centrally, so every screen recovers the
      // same way instead of each handling 401 itself.
      handleUnauthorized()
    }
    throw error
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

/** Builds a query string, omitting empty values so URLs stay readable. */
export function queryString(
  params: Record<string, string | number | undefined | null>,
): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const encoded = search.toString()
  return encoded ? `?${encoded}` : ''
}
