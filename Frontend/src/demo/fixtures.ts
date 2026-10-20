import type {
  AskQuota,
  FaceReview,
  ImportJob,
  Memory,
  MemoryStats,
  MemorySummary,
  PeopleGraph,
  PersonDetail,
  PersonSummary,
  PlaceDetail,
  PlaceSummary,
  PrivacyStatus,
  TripDetail,
  TripSummary,
  User,
} from '@/types/api'

const now = '2024-11-02T08:12:00Z'

export const demoUser: User = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'aarav@example.com',
  displayName: 'Aarav Mehta',
  createdAt: '2024-01-12T10:00:00Z',
}

function asset(memoryId: string, assetId: string, fileName: string) {
  return {
    id: assetId,
    fileName,
    mimeType: 'image/jpeg',
    sizeBytes: 420_000,
    widthPx: 1600,
    heightPx: 1200,
    createdAt: now,
    downloadPath: `/api/v1/memories/${memoryId}/media/${assetId}`,
  }
}

const tigerId = '22222222-2222-4222-8222-222222222222'
const buddhaId = '33333333-3333-4333-8333-333333333333'
const birthdayId = '44444444-4444-4444-8444-444444444444'
const chatBhutanId = '55555555-5555-4555-8555-555555555555'
const sikkimNoteId = '66666666-6666-4666-8666-666666666666'
const chatBirthdayId = '77777777-7777-4777-8777-777777777777'

const tigerAsset = 'a1111111-a111-4111-8111-a11111111111'
const buddhaAsset = 'a2222222-a222-4222-8222-a22222222222'
const birthdayAsset = 'a3333333-a333-4333-8333-a33333333333'

const priyaId = 'p1111111-1111-4111-8111-111111111111'
const momId = 'p2222222-2222-4222-8222-222222222222'
const rajId = 'p3333333-3333-4333-8333-333333333333'

const thimphuId = 'l1111111-1111-4111-8111-111111111111'
const gangtokId = 'l2222222-2222-4222-8222-222222222222'
const tripId = 't1111111-1111-4111-8111-111111111111'
const importId = 'i1111111-1111-4111-8111-111111111111'
const faceUnnamed = 'f1111111-1111-4111-8111-111111111111'
const faceSuggested = 'f2222222-2222-4222-8222-222222222222'
const clusterId = 'c1111111-1111-4111-8111-111111111111'

export const ids = {
  tigerId,
  buddhaId,
  birthdayId,
  tigerAsset,
  buddhaAsset,
  birthdayAsset,
  priyaId,
  momId,
  rajId,
  thimphuId,
  gangtokId,
  tripId,
  importId,
}

export const demoMemories: Memory[] = [
  {
    id: chatBhutanId,
    type: 'CONVERSATION',
    source: 'IMPORT',
    title: 'WhatsApp · Priya · 2019-04-02',
    description: 'From WhatsApp · Priya',
    occurredAt: '2019-04-02T14:20:00Z',
    processingStatus: 'COMPLETED',
    createdAt: now,
    updatedAt: now,
    assets: [],
    people: [
      { id: priyaId, displayName: 'Priya' },
      { id: rajId, displayName: 'Raj' },
    ],
    faces: [],
    messages: [
      {
        id: 'm1',
        sentAt: '2019-04-02T14:20:00Z',
        senderName: 'Priya',
        body: '7D/6N Best of Bhutan is ₹39,006 per adult if we book this week.',
        sortIndex: 0,
      },
      {
        id: 'm2',
        sentAt: '2019-04-02T14:22:00Z',
        senderName: 'Aarav',
        body: 'Tiger’s Nest hike is on day 5. Bagdogra → Phuentsholing first.',
        sortIndex: 1,
      },
      {
        id: 'm3',
        sentAt: '2019-04-02T14:25:00Z',
        senderName: 'Raj',
        body: 'Bhutan is expensive but the 5D private one is ₹26,200 + GST.',
        sortIndex: 2,
      },
    ],
  },
  {
    id: tigerId,
    type: 'PHOTO',
    source: 'IMPORT',
    title: 'Tiger’s Nest, Paro',
    description: 'From WhatsApp · Priya',
    content: 'Hike up to Taktsang. Clear sky after the mist burned off.',
    occurredAt: '2019-04-07T08:40:00Z',
    processingStatus: 'COMPLETED',
    createdAt: now,
    updatedAt: now,
    assets: [asset(tigerId, tigerAsset, 'IMG-20190407-Paro.jpg')],
    people: [{ id: priyaId, displayName: 'Priya' }],
    faces: [
      {
        id: faceSuggested,
        memoryId: tigerId,
        assetId: tigerAsset,
        x: 0.32,
        y: 0.18,
        width: 0.22,
        height: 0.28,
        suggestedPersonId: priyaId,
        suggestedPersonName: 'Priya',
        confidence: 0.86,
        clusterId,
      },
    ],
  },
  {
    id: buddhaId,
    type: 'PHOTO',
    source: 'IMPORT',
    title: 'Big Buddha, Thimphu',
    description: 'From WhatsApp · Priya',
    occurredAt: '2019-04-04T11:05:00Z',
    processingStatus: 'COMPLETED',
    createdAt: now,
    updatedAt: now,
    assets: [asset(buddhaId, buddhaAsset, 'IMG-20190404-Thimphu.jpg')],
    people: [
      { id: priyaId, displayName: 'Priya' },
      { id: rajId, displayName: 'Raj' },
    ],
    faces: [
      {
        id: faceUnnamed,
        memoryId: buddhaId,
        assetId: buddhaAsset,
        x: 0.41,
        y: 0.2,
        width: 0.2,
        height: 0.26,
        clusterId,
      },
    ],
  },
  {
    id: sikkimNoteId,
    type: 'TEXT',
    source: 'MANUAL',
    title: 'Sikkim trip',
    content: 'The train to Gangtok was delayed by four hours. We reached MG Marg after dark.',
    occurredAt: '2019-04-02T09:00:00Z',
    processingStatus: 'COMPLETED',
    createdAt: now,
    updatedAt: now,
    assets: [],
    people: [{ id: rajId, displayName: 'Raj' }],
    faces: [],
  },
  {
    id: birthdayId,
    type: 'PHOTO',
    source: 'UPLOAD',
    title: 'Birthday at home',
    content: 'Cake, cousins, the same song as every year.',
    occurredAt: '2023-08-19T18:30:00Z',
    processingStatus: 'COMPLETED',
    createdAt: now,
    updatedAt: now,
    assets: [asset(birthdayId, birthdayAsset, 'IMG-20230819-cake.jpg')],
    people: [{ id: momId, displayName: 'Mom' }],
    faces: [
      {
        id: 'f3333333-3333-4333-8333-333333333333',
        memoryId: birthdayId,
        assetId: birthdayAsset,
        x: 0.22,
        y: 0.28,
        width: 0.2,
        height: 0.28,
        personId: momId,
        personName: 'Mom',
      },
    ],
  },
  {
    id: chatBirthdayId,
    type: 'CONVERSATION',
    source: 'IMPORT',
    title: 'WhatsApp · Family · 2023-08-19',
    description: 'From WhatsApp · Family',
    occurredAt: '2023-08-19T16:00:00Z',
    processingStatus: 'COMPLETED',
    createdAt: now,
    updatedAt: now,
    assets: [],
    people: [{ id: momId, displayName: 'Mom' }],
    faces: [],
    messages: [
      {
        id: 'm4',
        sentAt: '2023-08-19T16:00:00Z',
        senderName: 'Mom',
        body: 'Cake is in the fridge. Come before 7.',
        sortIndex: 0,
      },
      {
        id: 'm5',
        sentAt: '2023-08-19T16:04:00Z',
        senderName: 'Aarav',
        body: 'On the way. Saving a slice for Priya.',
        sortIndex: 1,
      },
    ],
  },
]

export function toSummary(memory: Memory): MemorySummary {
  const excerpt = memory.content ?? memory.description ?? memory.messages?.[0]?.body
  return {
    id: memory.id,
    type: memory.type,
    source: memory.source,
    occurredAt: memory.occurredAt,
    processingStatus: memory.processingStatus,
    assets: memory.assets,
    ...(memory.title ? { title: memory.title } : {}),
    ...(excerpt ? { excerpt: excerpt.slice(0, 180) } : {}),
  }
}

export const demoPeople: PersonSummary[] = [
  { id: priyaId, displayName: 'Priya', memoryCount: 3, createdAt: now },
  { id: momId, displayName: 'Mom', memoryCount: 2, createdAt: now },
  { id: rajId, displayName: 'Raj', memoryCount: 3, createdAt: now },
]

export function personDetail(id: string): PersonDetail | undefined {
  const summary = demoPeople.find((person) => person.id === id)
  if (!summary) return undefined
  const linked = demoMemories.filter((memory) => memory.people.some((person) => person.id === id))
  const photos = linked.filter((memory) => memory.type === 'PHOTO').map(toSummary)
  const memories = linked.filter((memory) => memory.type !== 'PHOTO').map(toSummary)
  return {
    ...summary,
    connected: {
      conversations: linked.filter((memory) => memory.type === 'CONVERSATION').length,
      photos: photos.length,
      videos: 0,
      audio: 0,
      documents: 0,
      text: linked.filter((memory) => memory.type === 'TEXT').length,
      events: 0,
      places: 1,
    },
    memories,
    photos,
  }
}

export const demoPlaces: PlaceSummary[] = [
  {
    id: thimphuId,
    displayName: 'Thimphu',
    latitude: 27.4728,
    longitude: 89.639,
    geocoded: true,
    memoryCount: 2,
    createdAt: now,
  },
  {
    id: gangtokId,
    displayName: 'Gangtok',
    latitude: 27.3389,
    longitude: 88.6065,
    geocoded: true,
    memoryCount: 1,
    createdAt: now,
  },
]

export function placeDetail(id: string): PlaceDetail | undefined {
  const summary = demoPlaces.find((place) => place.id === id)
  if (!summary) return undefined
  const memories =
    id === thimphuId
      ? [demoMemories.find((item) => item.id === buddhaId), demoMemories.find((item) => item.id === tigerId)]
          .filter((item): item is Memory => item != null)
          .map(toSummary)
      : [demoMemories.find((item) => item.id === sikkimNoteId)]
          .filter((item): item is Memory => item != null)
          .map(toSummary)
  return { ...summary, memories }
}

export const demoTrips: TripSummary[] = [
  {
    id: tripId,
    title: 'Bhutan, April 2019',
    startedAt: '2019-04-02T00:00:00Z',
    endedAt: '2019-04-08T00:00:00Z',
    memoryCount: 4,
    placeCount: 2,
    personCount: 2,
    primaryPlaceName: 'Thimphu',
    createdAt: now,
  },
]

export const demoTripDetail: TripDetail = {
  id: tripId,
  title: 'Bhutan, April 2019',
  startedAt: '2019-04-02T00:00:00Z',
  endedAt: '2019-04-08T00:00:00Z',
  memoryCount: 4,
  createdAt: now,
  places: demoPlaces,
  people: demoPeople.filter((person) => person.id !== momId),
  memories: demoMemories
    .filter((memory) => memory.occurredAt.startsWith('2019-04'))
    .map(toSummary),
}

export const demoGraph: PeopleGraph = {
  nodes: demoPeople.map((person) => ({
    id: person.id,
    displayName: person.displayName,
    memoryCount: person.memoryCount,
  })),
  edges: [
    { fromPersonId: priyaId, toPersonId: rajId, sharedMemories: 2 },
    { fromPersonId: priyaId, toPersonId: momId, sharedMemories: 1 },
  ],
}

export const demoImports: ImportJob[] = [
  {
    id: importId,
    kind: 'WHATSAPP',
    status: 'COMPLETED',
    fileName: 'WhatsApp Chat - Priya.zip',
    zone: 'Asia/Kolkata',
    chatName: 'Priya',
    memoriesCreated: 4,
    createdAt: '2024-10-12T09:00:00Z',
    finishedAt: '2024-10-12T09:02:00Z',
  },
]

export const demoStats: MemoryStats = {
  totalMemories: demoMemories.length,
  earliestOccurredAt: '2019-04-02T09:00:00Z',
  totalPeople: demoPeople.length,
  totalPlaces: demoPlaces.length,
}

export const demoPrivacy: PrivacyStatus = {
  dataStoredLocally: true,
  storageBackend: 'LOCAL',
  languageModelEnabled: true,
  languageModel: 'demo',
  embeddingsEnabled: true,
  facesEnabled: true,
  facesRunLocally: true,
}

export const demoQuota: AskQuota = {
  capped: false,
  limit: 40,
  used: 3,
  remaining: 37,
}

export const demoFaceReview: FaceReview = {
  unlabeledCount: 1,
  suggestedCount: 1,
  groups: [
    {
      clusterId,
      size: 2,
      suggestedPersonId: priyaId,
      suggestedPersonName: 'Priya',
      faces: [
        {
          id: faceSuggested,
          memoryId: tigerId,
          memoryTitle: 'Tiger’s Nest, Paro',
          memoryType: 'PHOTO',
          occurredAt: '2019-04-07T08:40:00Z',
          assetId: tigerAsset,
          downloadPath: `/api/v1/memories/${tigerId}/media/${tigerAsset}`,
          x: 0.32,
          y: 0.18,
          width: 0.22,
          height: 0.28,
          suggestedPersonId: priyaId,
          suggestedPersonName: 'Priya',
          confidence: 0.86,
          clusterId,
        },
        {
          id: faceUnnamed,
          memoryId: buddhaId,
          memoryTitle: 'Big Buddha, Thimphu',
          memoryType: 'PHOTO',
          occurredAt: '2019-04-04T11:05:00Z',
          assetId: buddhaAsset,
          downloadPath: `/api/v1/memories/${buddhaId}/media/${buddhaAsset}`,
          x: 0.41,
          y: 0.2,
          width: 0.2,
          height: 0.26,
          clusterId,
        },
      ],
    },
  ],
}
