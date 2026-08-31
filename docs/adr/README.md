# Architecture decision records

Short records of decisions that are expensive to reverse, and why they were made. Each one states
the context, the decision, the consequences we accepted, and the alternatives we rejected.

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-modular-monolith.md) | Modular monolith, not microservices | Accepted |
| [0002](0002-memory-as-central-abstraction.md) | Memory is the central abstraction | Accepted |
| [0003](0003-postgres-with-pgvector.md) | PostgreSQL with pgvector for hybrid retrieval | Accepted |
| [0004](0004-stateless-jwt-authentication.md) | Stateless JWT authentication | Accepted |
| [0005](0005-flyway-owns-the-schema.md) | Flyway owns the schema | Accepted |
| [0006](0006-api-response-envelope.md) | A single API response envelope | Accepted |
| [0007](0007-object-storage-abstraction.md) | Object storage is abstracted; media never touches the database | Accepted |
| [0008](0008-asynchronous-processing-pipeline.md) | Persisted jobs for asynchronous enrichment | Accepted |
| [0009](0009-upload-validation.md) | Magic-byte sniffing for uploads | Accepted |
| [0010](0010-postgres-full-text-search.md) | PostgreSQL full-text search behind `MemorySearcher` | Accepted |
| [0011](0011-embeddings-hybrid-ask.md) | Embeddings, hybrid retrieval, and grounded Ask | Accepted |
| [0012](0012-whatsapp-day-bucket-import.md) | WhatsApp export import as day-bucketed conversations | Accepted |
| [0013](0013-photo-enrichment.md) | Photo enrichment: EXIF, OCR, captions, HEIC | Accepted |
| [0014](0014-conversation-messages-satellite.md) | Conversation messages as satellite of day buckets | Accepted |
| [0015](0015-people-from-senders.md) | People from chat senders; face clustering deferred | Accepted |
| [0016](0016-av-places-graph.md) | Audio/video transcription, Places, people graph UX | Accepted |
| [0017](0017-google-photos-takeout.md) | Google Photos via Takeout (not Library API sync) | Accepted |
| [0018](0018-google-photos-picker-and-geocode.md) | Photos Picker OAuth + reverse-geocoded place names | Accepted |
| [0019](0019-free-local-faces.md) | Free local InsightFace sidecar + confirm UI | Accepted |
