import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo } from 'react'

import { memoriesApi } from '@/features/memories/memoriesApi'

interface AuthenticatedImageProps {
  downloadPath: string
  alt: string
  className?: string
}

/**
 * Renders a protected image.
 *
 * A plain `src` cannot carry the bearer token, and putting the token in the URL would leak it into
 * browser history and any proxy logs along the way. So the bytes are fetched like any other API call
 * and handed to the tag as an object URL.
 */
export function AuthenticatedImage({ downloadPath, alt, className }: AuthenticatedImageProps) {
  const objectUrl = useAuthenticatedObjectUrl(downloadPath)

  if (objectUrl === 'error') {
    return (
      <div
        className={`flex items-center justify-center bg-surface-raised text-xs text-fg-muted ${className ?? ''}`}
        role="img"
        aria-label={`${alt} (unavailable)`}
      >
        Unavailable
      </div>
    )
  }

  if (!objectUrl) {
    return <div className={`animate-pulse bg-surface-raised ${className ?? ''}`} aria-hidden />
  }

  return <img src={objectUrl} alt={alt} className={className} />
}

export function AuthenticatedVideo({
  downloadPath,
  className,
}: {
  downloadPath: string
  className?: string
}) {
  const objectUrl = useAuthenticatedObjectUrl(downloadPath)
  if (objectUrl === 'error') {
    return (
      <div className={`flex items-center justify-center bg-surface-raised text-xs text-fg-muted ${className ?? ''}`}>
        Unavailable
      </div>
    )
  }
  if (!objectUrl) {
    return <div className={`h-48 animate-pulse rounded-panel bg-surface-raised ${className ?? ''}`} aria-hidden />
  }
  return <video src={objectUrl} controls className={className} />
}

export function AuthenticatedAudio({ downloadPath }: { downloadPath: string }) {
  const objectUrl = useAuthenticatedObjectUrl(downloadPath)
  if (objectUrl === 'error') return <p className="text-sm text-fg-muted">Audio unavailable</p>
  if (!objectUrl) return <div className="h-10 animate-pulse rounded-xl bg-surface-raised" aria-hidden />
  return <audio src={objectUrl} controls className="w-full" />
}

function useAuthenticatedObjectUrl(downloadPath: string): string | undefined | 'error' {
  const { data: blob, isError } = useQuery({
    queryKey: ['media', downloadPath],
    queryFn: () => memoriesApi.media(downloadPath),
    staleTime: Infinity,
  })

  const objectUrl = useMemo(() => (blob ? URL.createObjectURL(blob) : undefined), [blob])

  useEffect(() => {
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [objectUrl])

  if (isError) return 'error'
  return objectUrl
}
