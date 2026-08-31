import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

import { ApiRequestError } from '@/lib/ApiRequestError'
import { clearSession, readAccessToken } from '@/lib/session'
import type { ApiEnvelope } from '@/types/api'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  // Imports and busy enrichment can stall the pool briefly; 20s felt like "signed out" / stuck UI.
  timeout: 60_000,
})

http.interceptors.request.use((config) => {
  const token = readAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // A multipart body needs a generated boundary in its content type, which the browser only supplies
  // when the instance-wide JSON default is out of the way.
  if (config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
})

type SessionExpiredListener = () => void

const sessionExpiredListeners = new Set<SessionExpiredListener>()

/**
 * Lets the auth provider react to a token the backend no longer accepts, without the API layer
 * needing to know about React state or routing.
 */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener)
  return () => sessionExpiredListeners.delete(listener)
}

function toApiRequestError(error: unknown): ApiRequestError {
  if (!(error instanceof AxiosError)) {
    return ApiRequestError.unexpected()
  }
  if (!error.response) {
    return ApiRequestError.unreachable()
  }

  const status = error.response.status
  const body = (error.response.data as ApiEnvelope<never> | undefined)?.error

  if (status === 401) {
    // Media/blob 401s during overload should not nuke the whole session; only API envelope auth
    // failures (and /auth/me) should. Callers that want logout already use onSessionExpired via
    // normal JSON requests — still notify, but avoid double-clear races from parallel image loads.
    const url = String(error.config?.url ?? '')
    const isMedia = error.config?.responseType === 'blob' || url.includes('/media/')
    if (!isMedia) {
      clearSession()
      sessionExpiredListeners.forEach((listener) => listener())
    }
  }

  return body ? ApiRequestError.fromBody(body, status) : ApiRequestError.unexpected(status)
}

/**
 * Unwraps the backend envelope so callers work with plain payloads, and converts every failure into
 * an {@link ApiRequestError}.
 * <p>
 * DELETE endpoints often return {@code 204 No Content} with an empty body (no envelope). Treat those
 * as success so mutations still run {@code onSuccess} and the UI can refresh.
 */
async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiEnvelope<T> | '' | null>(config)
    if (response.status === 204 || response.data == null || response.data === '') {
      return undefined as T
    }
    const envelope = response.data
    if (typeof envelope !== 'object' || !('success' in envelope)) {
      throw ApiRequestError.unexpected(response.status)
    }
    if (!envelope.success || envelope.data === undefined) {
      throw envelope.error
        ? ApiRequestError.fromBody(envelope.error, response.status)
        : ApiRequestError.unexpected(response.status)
    }
    return envelope.data
  } catch (error) {
    throw error instanceof ApiRequestError ? error : toApiRequestError(error)
  }
}

/**
 * Fetches a response that is not wrapped in the envelope, such as media bytes. Errors still come back
 * as an {@link ApiRequestError}, but the body is read as a blob rather than parsed.
 */
async function requestBlob(url: string, config?: AxiosRequestConfig): Promise<Blob> {
  try {
    const response = await http.request<Blob>({ ...config, method: 'GET', url, responseType: 'blob' })
    return response.data
  } catch (error) {
    throw toApiRequestError(error)
  }
}

export const api = {
  get: <T>(url: string, config?: AxiosRequestConfig) => request<T>({ ...config, method: 'GET', url }),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'POST', url, data }),
  patch: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'PATCH', url, data }),
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'DELETE', url }),

  /**
   * Posts multipart form data. The timeout is removed because a large upload over a slow connection
   * legitimately takes far longer than an API call, and cancelling it half-way is worse than waiting.
   */
  postForm: <T>(url: string, form: FormData, onProgress?: (percent: number) => void) =>
    request<T>({
      method: 'POST',
      url,
      data: form,
      timeout: 0,
      ...(onProgress && {
        onUploadProgress: (event) =>
          onProgress(event.total ? Math.round((event.loaded / event.total) * 100) : 0),
      }),
    }),

  getBlob: requestBlob,
}
