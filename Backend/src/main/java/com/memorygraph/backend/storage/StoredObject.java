package com.memorygraph.backend.storage;

/** What the store recorded about an object after writing it. */
public record StoredObject(StorageKey key, long sizeBytes, String checksum) {
}
