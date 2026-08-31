import { QueryClient } from '@tanstack/react-query'

import { ApiRequestError } from '@/lib/ApiRequestError'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Retrying a rejected token or a validation error only delays the error the user needs to see.
      retry: (failureCount, error) => {
        if (error instanceof ApiRequestError && error.status && error.status < 500) return false
        return failureCount < 2
      },
      // A personal archive is often left open in a background tab. Refetching on focus is cheap and
      // avoids returning to a stale count after adding a memory somewhere else.
      refetchOnWindowFocus: true,
    },
  },
})
