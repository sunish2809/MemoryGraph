import type { MemoryType } from '@/types/api'

/** Display names for memory types. Kept out of component files so Fast Refresh can do its job. */
export const MEMORY_TYPE_LABELS: Record<MemoryType, string> = {
  TEXT: 'Note',
  PHOTO: 'Photo',
  VIDEO: 'Video',
  AUDIO: 'Audio',
  DOCUMENT: 'Document',
  CONVERSATION: 'Conversation',
  EVENT: 'Event',
}
