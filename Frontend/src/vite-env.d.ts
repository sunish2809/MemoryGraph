/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Base URL for the API. Defaults to a relative `/api/v1`, which is served by the Vite dev proxy
   * in development and by the frontend container's reverse proxy in Docker, so the browser always
   * talks to a single origin.
   */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
