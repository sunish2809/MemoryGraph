# 8. Enrichment runs asynchronously, driven by persisted jobs

Date: 2026-08-22

## Status

Accepted

## Context

An uploaded file needs work done to it before it is fully useful: today reading image dimensions and
assembling searchable text, later EXIF extraction, OCR, captioning, transcription and embedding
generation. Some of that is fast; running a model over a video is not.

The user is waiting on an HTTP response. What they actually need from it is confirmation that their
file is safe — not that it has been analysed.

Phase 4 introduces embeddings and Phase 5 introduces media extraction, so whatever we build now
becomes the thing those phases plug into.

## Decision

**Uploads respond as soon as the bytes are stored and the metadata is committed.** The memory comes
back with `processingStatus: PENDING`.

**Each unit of work is a row in `processing_jobs`**, not a task held in memory. A job records its type,
owner, memory, status, attempt count, next eligible time, duration and error message.

**Work is claimed, then executed, then recorded** — three short transactions rather than one long one,
so `PROCESSING` is a state that is actually visible while the work runs, and no row stays locked for
the duration of the work.

**Two paths lead to the same runner.** A fast path dispatches immediately after commit, via a
`@TransactionalEventListener(AFTER_COMMIT)` on a bounded executor. A `@Scheduled` sweeper polls for
work that is due. The fast path is an optimisation; the sweeper is the guarantee.

**Failures retry with backoff** up to an attempt budget, doubling the delay each time.

**The processing step is an interface.** `MemoryProcessor` implementations are looked up by job type,
so a new pipeline stage is a new bean and a new enum constant.

## Consequences

### What this buys

The upload response is fast and stays fast as enrichment gets more expensive. Adding a transcription
step will not make uploading a photo slower.

**Work survives everything.** A process killed mid-job leaves a row in `PROCESSING`; the sweeper
reclaims it once `stale-after` elapses. A job whose event dispatch was lost because the executor's
queue was full is still `PENDING` and gets picked up. There is no state in which work is silently
dropped — which matters, because the user has been told their memory is saved.

The claim query uses row locks with `SKIP LOCKED`, so running more than one instance of the
application is safe without any coordination service.

Every job carries its own id, memory id, user id, attempt count, duration and error, which is exactly
what is needed to answer "why does this photo have no dimensions" for a specific person.

**This is the seam a message broker slots into.** Replacing the sweeper with a consumer changes nothing
about how work is produced, retried or observed. Persisting jobs first is what makes that swap a
substitution rather than a redesign.

### What this costs

**A memory is briefly incomplete.** A freshly uploaded photo has no dimensions and no searchable text
for a fraction of a second, and the API returns it that way. The frontend handles this by polling only
while the status is unfinished, and by showing a "Processing" badge — but it is real complexity that a
synchronous implementation would not have.

**Polling is not free.** The sweeper runs a query every 30 seconds whether or not there is work. A
partial index on unfinished statuses keeps it cheap, but it is a constant trickle of database traffic,
and it means a retry can sit idle for up to one sweep interval.

**Three transactions per attempt instead of one.** More round trips, and a crash between them is
possible — which is precisely why stale-job reclamation exists rather than being optional.

**Text memories bypass the pipeline entirely** and are marked `COMPLETED` on arrival, because there is
nothing to extract from text the user just typed. This is honest today but will change in Phase 4,
where text needs an embedding like everything else.

**The executor is bounded, and pushes back when full.** Enrichment decodes media and will later run
models, so unbounded concurrency would let a burst of uploads starve the threads serving HTTP
requests. When the queue fills, the caller runs the task: the upload that caused the congestion waits,
rather than work being dropped or the server falling over.

## Alternatives considered

**Do the work inline, during the upload request.** Far simpler, and for reading PNG dimensions it would
be imperceptible. Rejected because it does not survive contact with Phase 5: a synchronous transcription
would hold a request thread for minutes, and by then the choice is entangled with the API contract.

**In-memory queue, no database rows.** Less schema, less code. Rejected because a restart loses work
with no record that it was ever meant to happen. There would be no way to answer why a photo is missing
its metadata, and no way to retry.

**A message broker now.** Kafka or RabbitMQ solves durability and distribution properly. Rejected as
premature: it is a service to run, secure and monitor, for a workload one process handles comfortably.
The point of persisting jobs is that adopting a broker later is a substitution, not a rewrite.

**`@Retryable` in-process retries.** Neat for transient failures. Rejected because retries that live
only in a thread's stack vanish on restart, and the attempt count would not be visible to anyone.
