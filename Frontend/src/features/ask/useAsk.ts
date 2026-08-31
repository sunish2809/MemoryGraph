import { useMutation } from '@tanstack/react-query'

import { askApi } from '@/features/ask/askApi'
import type { AskRequest } from '@/types/api'

/** Runs Ask only when the caller submits a question. */
export function useAsk() {
  return useMutation({
    mutationFn: (payload: AskRequest) => askApi.ask(payload),
  })
}
