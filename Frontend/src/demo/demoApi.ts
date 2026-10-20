import type { AxiosRequestConfig } from 'axios'

import { birthdayPortrait, genericPortrait, thimphuPortrait, tigerNestPortrait } from '@/demo/portraits'
import {
  demoFaceReview,
  demoGraph,
  demoImports,
  demoMemories,
  demoPeople,
  demoPlaces,
  demoPrivacy,
  demoQuota,
  demoStats,
  demoTripDetail,
  demoTrips,
  demoUser,
  ids,
  personDetail,
  placeDetail,
  toSummary,
} from '@/demo/fixtures'
import { ApiRequestError } from '@/lib/ApiRequestError'
import type {
  AskRequest,
  AskResponse,
  AskSource,
  AuthenticationResult,
  Memory,
  Page,
  SearchResult,
  Timeline,
} from '@/types/api'

const AUTH: AuthenticationResult = {
  accessToken: 'demo-token',
  tokenType: 'Bearer',
  expiresAt: '2099-01-01T00:00:00Z',
  user: demoUser,
}

export function demoAuthResult(): AuthenticationResult {
  return AUTH
}

function delay(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 120))
}

function normalizePath(url: string | undefined): string {
  const raw = (url ?? '').split('?')[0] ?? ''
  const withoutBase = raw.replace(/^https?:\/\/[^/]+/, '')
  return withoutBase.replace(/^\/api\/v1/, '') || '/'
}

function notFound(label: string): never {
  throw new ApiRequestError(`${label} was not found.`, 'RESOURCE_NOT_FOUND', 404)
}

function pageOf<T>(items: T[], page = 0, size = 20): Page<T> {
  const start = page * size
  const slice = items.slice(start, start + size)
  const totalPages = Math.max(1, Math.ceil(items.length / size))
  return {
    items: slice,
    page,
    size,
    totalItems: items.length,
    totalPages,
    hasNext: page + 1 < totalPages,
  }
}

function param(config: AxiosRequestConfig, name: string): string | undefined {
  const value = (config.params as Record<string, unknown> | undefined)?.[name]
  if (value == null) return undefined
  return String(value)
}

function timeline(): Timeline {
  const byDay = new Map<string, Memory[]>()
  const sorted = [...demoMemories].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
  for (const memory of sorted) {
    const date = memory.occurredAt.slice(0, 10)
    const list = byDay.get(date) ?? []
    list.push(memory)
    byDay.set(date, list)
  }
  const days = [...byDay.entries()].map(([date, memories]) => ({
    date,
    memories: memories.map(toSummary),
  }))
  return {
    days,
    zone: 'Asia/Kolkata',
    page: 0,
    size: 50,
    totalItems: demoMemories.length,
    totalPages: 1,
    hasNext: false,
  }
}

function asSource(memory: Memory): AskSource {
  return {
    id: memory.id,
    type: memory.type,
    source: memory.source,
    occurredAt: memory.occurredAt,
    processingStatus: memory.processingStatus,
    people: memory.people.map((person) => person.displayName),
    assets: memory.assets,
    messages: memory.messages ?? [],
    relatedPhotos: [],
    ...(memory.title ? { title: memory.title } : {}),
    ...(memory.description ? { description: memory.description } : {}),
    ...(memory.content ? { excerpt: memory.content } : {}),
  }
}

function ask(body: unknown): AskResponse {
  const question = typeof body === 'object' && body && 'question' in body ? String((body as AskRequest).question) : ''
  const lower = question.toLowerCase()
  const bhutan = demoMemories.filter((memory) => memory.occurredAt.startsWith('2019-04'))
  const birthday = demoMemories.filter((memory) => memory.id === ids.birthdayId || memory.title?.includes('Family'))

  if (lower.includes('birthday') || lower.includes('cake')) {
    return {
      question,
      answer:
        'The birthday at home is 19 August 2023. Mom asked you to come before 7; the cake was in the fridge, and you saved a slice for Priya.',
      grounded: true,
      model: 'demo',
      sources: birthday.map(asSource),
      quota: demoQuota,
    }
  }

  return {
    question,
    answer:
      'In April 2019 you travelled Bhutan with Priya and Raj. WhatsApp on 2 April compared packages (₹39,006 for 7D/6N). You hiked Tiger’s Nest on 7 April and visited the Big Buddha in Thimphu on 4 April.',
    grounded: true,
    model: 'demo',
    sources: bhutan.map(asSource),
    quota: demoQuota,
  }
}

function search(config: AxiosRequestConfig): Page<SearchResult> {
  const q = (param(config, 'q') ?? '').toLowerCase()
  const hits = demoMemories
    .filter((memory) => {
      if (!q) return true
      const blob = [memory.title, memory.content, memory.description, ...(memory.messages?.map((m) => m.body) ?? [])]
        .join(' ')
        .toLowerCase()
      return blob.includes(q)
    })
    .map((memory) => ({ memory: toSummary(memory) }))
  return pageOf(hits, Number(param(config, 'page') ?? 0), Number(param(config, 'size') ?? 20))
}

export async function handleDemoRequest<T>(config: AxiosRequestConfig): Promise<T> {
  await delay()
  const path = normalizePath(config.url)
  const method = (config.method ?? 'get').toUpperCase()

  if (path === '/auth/login' || path === '/auth/register') return AUTH as T
  if (path === '/auth/me' || path === '/auth/registration') {
    return (path === '/auth/me' ? demoUser : { inviteRequired: false }) as T
  }
  if (path === '/health') {
    return { status: 'UP', application: 'memorygraph-demo', version: '0.1.0', timestamp: new Date().toISOString() } as T
  }
  if (path === '/account/privacy') return demoPrivacy as T
  if (path === '/memories/stats') return demoStats as T
  if (path === '/memories') return pageOf(demoMemories.map(toSummary), Number(param(config, 'page') ?? 0)) as T
  if (path === '/timeline') return timeline() as T
  if (path === '/search') return search(config) as T
  if (path === '/people') return demoPeople as T
  if (path === '/people/graph') return demoGraph as T
  if (path === '/places') return demoPlaces as T
  if (path === '/trips') return { trips: demoTrips, suggestions: [] } as T
  if (path === '/imports') return demoImports as T
  if (path === '/faces/review') return demoFaceReview as T
  if (path === '/ask/quota') return demoQuota as T
  if (path === '/ask' && method === 'POST') return ask(config.data) as T
  if (path === '/integrations/google') return { configured: false, connected: false } as T

  const memoryMatch = path.match(/^\/memories\/([^/]+)$/)
  if (memoryMatch) {
    const memory = demoMemories.find((item) => item.id === memoryMatch[1])
    if (!memory) notFound('Memory')
    return memory as T
  }
  const personMatch = path.match(/^\/people\/([^/]+)$/)
  if (personMatch) {
    const detail = personDetail(personMatch[1])
    if (!detail) notFound('Person')
    return detail as T
  }
  const placeMatch = path.match(/^\/places\/([^/]+)$/)
  if (placeMatch) {
    const detail = placeDetail(placeMatch[1])
    if (!detail) notFound('Place')
    return detail as T
  }
  if (path === `/trips/${ids.tripId}`) return demoTripDetail as T
  if (path.startsWith('/imports/')) return demoImports[0] as T

  if (method === 'POST' && (path === '/memories/text' || path === '/memories/upload')) {
    const created: Memory = {
      id: crypto.randomUUID(),
      type: path.endsWith('upload') ? 'PHOTO' : 'TEXT',
      source: path.endsWith('upload') ? 'UPLOAD' : 'MANUAL',
      title: 'Demo memory',
      content: 'Saved only in this browser session.',
      occurredAt: new Date().toISOString(),
      processingStatus: 'COMPLETED',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      assets: [],
      people: [],
      faces: [],
    }
    demoMemories.unshift(created)
    return created as T
  }
  if (method === 'DELETE') return undefined as T
  if (method === 'PATCH' || method === 'POST') {
    if (path.includes('/faces') || path.includes('/people') || path.includes('/clusters')) {
      return demoFaceReview as T
    }
    const memoryPatch = path.match(/^\/memories\/([^/]+)$/)
    if (memoryPatch) {
      const memory = demoMemories.find((item) => item.id === memoryPatch[1])
      if (!memory) notFound('Memory')
      return memory as T
    }
    return { message: 'Demo accepted this action in-memory only.' } as T
  }

  throw new ApiRequestError(`Demo has no handler for ${method} ${path}`, 'INTERNAL_ERROR', 500)
}

export async function handleDemoBlob(url: string): Promise<Blob> {
  await delay()
  const path = normalizePath(url)
  if (path.includes(ids.tigerAsset)) return tigerNestPortrait()
  if (path.includes(ids.buddhaAsset)) return thimphuPortrait()
  if (path.includes(ids.birthdayAsset)) return birthdayPortrait()
  return genericPortrait()
}
