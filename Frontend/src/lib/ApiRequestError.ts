import type { ApiErrorBody, ApiErrorCode } from '@/types/api'

/**
 * Normalised failure thrown by the API client, so callers never have to inspect Axios internals or
 * guess at the response shape.
 */
export class ApiRequestError extends Error {
  readonly code: ApiErrorCode
  readonly status: number | undefined
  readonly fieldErrors: Record<string, string>

  constructor(message: string, code: ApiErrorCode, status?: number, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors ?? {}
  }

  static fromBody(body: ApiErrorBody, status?: number): ApiRequestError {
    return new ApiRequestError(body.message, body.code, status, body.fieldErrors)
  }

  static unreachable(): ApiRequestError {
    return new ApiRequestError(
      'Could not reach the server. Check that the backend is running.',
      'NETWORK_ERROR',
    )
  }

  static unexpected(status?: number): ApiRequestError {
    return new ApiRequestError('Something went wrong. Please try again.', 'INTERNAL_ERROR', status)
  }

  /** Field-level message for a form input, if the backend reported one. */
  fieldError(field: string): string | undefined {
    return this.fieldErrors[field]
  }
}
