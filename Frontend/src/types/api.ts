/** Mirrors the backend's `ApiResponse` envelope. */
export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: ApiErrorBody
  timestamp: string
  requestId?: string
}

export interface ApiErrorBody {
  code: ApiErrorCode
  message: string
  fieldErrors?: Record<string, string>
}

/** Stable error codes the backend promises to keep; used to branch on failures. */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'MALFORMED_REQUEST'
  | 'INVALID_CREDENTIALS'
  | 'UNAUTHENTICATED'
  | 'ACCOUNT_DISABLED'
  | 'ACCESS_DENIED'
  | 'INVITE_INVALID'
  | 'RESOURCE_NOT_FOUND'
  | 'EMAIL_ALREADY_REGISTERED'
  | 'NAME_ALREADY_TAKEN'
  | 'PAYLOAD_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR'

export interface User {
  id: string
  email: string
  displayName: string
  createdAt: string
}

export interface PrivacyStatus {
  dataStoredLocally: boolean
  storageBackend: string
  languageModelEnabled: boolean
  languageModel: string
  embeddingsEnabled: boolean
  facesEnabled: boolean
  facesRunLocally: boolean
}

export interface AuthenticationResult {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

export interface HealthStatus {
  status: string
  application: string
  version: string
  timestamp: string
}

export type MemoryType = 'TEXT' | 'PHOTO' | 'VIDEO' | 'AUDIO' | 'DOCUMENT' | 'CONVERSATION' | 'EVENT'

export type MemorySource = 'MANUAL' | 'UPLOAD' | 'IMPORT'

export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'

export interface MediaAsset {
  id: string
  fileName: string
  mimeType: string
  sizeBytes: number
  widthPx?: number
  heightPx?: number
  latitude?: number
  longitude?: number
  capturedAt?: string
  createdAt: string
  /** API path the bytes are fetched from; requires the caller's bearer token. */
  downloadPath: string
}

/** A memory as it appears in a list: text is trimmed to an excerpt. */
export interface MemorySummary {
  id: string
  type: MemoryType
  source: MemorySource
  title?: string
  excerpt?: string
  occurredAt: string
  processingStatus: ProcessingStatus
  assets: MediaAsset[]
}

export interface LinkedPerson {
  id: string
  displayName: string
}

export interface FaceDetection {
  id: string
  memoryId?: string
  assetId: string
  x: number
  y: number
  width: number
  height: number
  personId?: string
  personName?: string
  suggestedPersonId?: string
  suggestedPersonName?: string
  confidence?: number
  clusterId?: string
  alsoSuggested?: number
  ignored?: boolean
}

export interface FaceReviewItem {
  id: string
  memoryId: string
  memoryTitle?: string
  memoryType: MemoryType
  occurredAt?: string
  assetId: string
  downloadPath: string
  x: number
  y: number
  width: number
  height: number
  suggestedPersonId?: string
  suggestedPersonName?: string
  confidence?: number
  clusterId?: string
}

export interface FaceClusterGroup {
  clusterId?: string
  size: number
  suggestedPersonId?: string
  suggestedPersonName?: string
  faces: FaceReviewItem[]
}

export interface FaceReview {
  unlabeledCount: number
  suggestedCount: number
  groups: FaceClusterGroup[]
}

export interface Memory {
  id: string
  type: MemoryType
  source: MemorySource
  title?: string
  description?: string
  content?: string
  occurredAt: string
  processingStatus: ProcessingStatus
  createdAt: string
  updatedAt: string
  assets: MediaAsset[]
  /** Present for conversation day-buckets created after Phase 6; empty for older imports. */
  messages?: ConversationMessage[]
  people: LinkedPerson[]
  faces: FaceDetection[]
}

export interface ConversationMessage {
  id: string
  sentAt: string
  senderName: string
  body: string
  sortIndex: number
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  hasNext: boolean
}

export interface MemoryStats {
  totalMemories: number
  /** Absent until the first memory is saved. */
  earliestOccurredAt?: string
  totalPeople: number
  totalPlaces: number
}

export interface PersonSummary {
  id: string
  displayName: string
  memoryCount: number
  createdAt: string
}

export interface PersonDetail {
  id: string
  displayName: string
  memoryCount: number
  createdAt: string
  connected: {
    conversations: number
    photos: number
    videos: number
    audio: number
    documents: number
    text: number
    events: number
    places: number
  }
  /** Recent non-photo memories (chats, notes). */
  memories: MemorySummary[]
  /** Photo gallery for this person. */
  photos: MemorySummary[]
}

export interface PlaceSummary {
  id: string
  displayName: string
  latitude: number
  longitude: number
  geocoded: boolean
  nameLocked?: boolean
  memoryCount: number
  createdAt: string
}

export interface PlaceDetail {
  id: string
  displayName: string
  latitude: number
  longitude: number
  geocoded: boolean
  nameLocked?: boolean
  memoryCount: number
  createdAt: string
  memories: MemorySummary[]
}

export interface TripSummary {
  id: string
  title: string
  startedAt: string
  endedAt: string
  notes?: string
  memoryCount: number
  placeCount: number
  personCount: number
  primaryPlaceName?: string
  createdAt: string
}

export interface TripSuggestion {
  title: string
  startedAt: string
  endedAt: string
  memoryCount: number
  placeCount: number
  personCount: number
  primaryPlaceName?: string
  places: PlaceSummary[]
  people: PersonSummary[]
}

export interface TripsPage {
  trips: TripSummary[]
  suggestions: TripSuggestion[]
}

export interface TripDetail {
  id: string
  title: string
  startedAt: string
  endedAt: string
  notes?: string
  memoryCount: number
  createdAt: string
  places: PlaceSummary[]
  people: PersonSummary[]
  memories: MemorySummary[]
}

export interface PeopleGraph {
  nodes: Array<{ id: string; displayName: string; memoryCount: number }>
  edges: Array<{ fromPersonId: string; toPersonId: string; sharedMemories: number }>
}

export interface TimelineDay {
  /** Calendar date in the requested timezone, as `YYYY-MM-DD`. */
  date: string
  memories: MemorySummary[]
}

export interface Timeline {
  days: TimelineDay[]
  zone: string
  page: number
  size: number
  totalItems: number
  totalPages: number
  hasNext: boolean
}

export type SearchSort = 'RELEVANCE' | 'NEWEST' | 'OLDEST'

/**
 * One search hit. `snippet` is present only when text was matched: matched words are wrapped in
 * `[[` `]]` so the client can highlight them as text rather than as markup.
 */
export interface SearchResult {
  memory: MemorySummary
  snippet?: string | undefined
}

export interface AskRequest {
  question: string
  from?: string | undefined
  to?: string | undefined
  zone?: string | undefined
  type?: MemoryType[] | undefined
}

export interface AskRelatedPhoto {
  id: string
  title?: string
  description?: string
  occurredAt: string
  assets: MediaAsset[]
}

/** Ask evidence item: may include chat lines and related WhatsApp-day photos. */
export interface AskSource {
  id: string
  type: MemoryType
  source: MemorySource
  title?: string
  description?: string
  excerpt?: string
  occurredAt: string
  processingStatus: ProcessingStatus
  people: string[]
  assets: MediaAsset[]
  messages: ConversationMessage[]
  relatedPhotos: AskRelatedPhoto[]
}

export interface AskResponse {
  question: string
  answer: string
  grounded: boolean
  model: string
  notice?: string
  sources: AskSource[]
}

export type ImportKind = 'WHATSAPP' | 'GOOGLE_PHOTOS' | 'GOOGLE_PHOTOS_PICKER'

export type ImportJobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface ImportJob {
  id: string
  kind: ImportKind
  status: ImportJobStatus
  fileName: string
  zone: string
  chatName?: string
  memoriesCreated: number
  errorMessage?: string
  createdAt: string
  finishedAt?: string
}

