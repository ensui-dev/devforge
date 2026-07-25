import { ApiError } from '../api/client'

/**
 * Turns a thrown value into a message worth showing.
 *
 * Prefers the backend's message, which is already written for a human to read.
 */
export function describeError(error: unknown, fallback = 'Something went wrong.'): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
