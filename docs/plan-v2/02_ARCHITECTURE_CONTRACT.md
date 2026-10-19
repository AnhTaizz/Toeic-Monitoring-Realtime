# 02 - Architecture and contracts (proposed V2 amendments)

## Keep the existing network architecture

```text
Candidate JavaFX + candidate-only observers
  ProcessHandleSnapshotSource -> ProcessCollector (existing)
  ExamWindow/Focus observer    -> new events
  USBStorageObserver (Windows) -> new events
  DisplayObserver             -> new events
  ExamActivityObserver (P1)    -> new events
      |    HTTP REST (exam, image/audio, save, submit, status)
      +--> WebSocket JSON (heartbeat, events, FULL/DELTA, START)
                         |
                 Spring Boot Server
                  /            \
             PostgreSQL     monitoring state
         (exam + history)   (bounded RAM, epoch)
                         |
           Scoped proctor WebSocket/HTTP client
                         |
            JavaFX unified proctor dashboard
```

Transport and event semantics already exist for process-related events. Extend carefully instead of cloning independent connection/ACK implementations. Existing process current-state FULL/DELTA is **not** the same thing as a durable history of multi-signal events.

## Rich TOEIC media design

- PostgreSQL: `exams`, `exam_questions`, `exam_options`, `exam_sessions` already exist. Add a numbered Flyway migration **after identifying the current latest version**, likely new media metadata and ordered `TEXT`/`IMAGE` content blocks tied to question or group; do not rewrite applied migrations.
- Media binary files: keep the established `TOEIC_AUDIO_ROOT/<examId>/<audioFile>` behavior untouched. An optional separately configured image root is proposed and must be approved; never presume it exists in the repo.
- Import: HTML/other permitted material -> offline normalization -> validation/report -> one-time seed/import -> immutable exam reference for many sessions. Do not invent original English answers or bundle third-party protected material without rights.
- API: scoped exam **catalog metadata only**; authorized image content delivery; existing audio manifest/download/READY preserved; no correct answer keys in candidate DTO, media metadata, log or client bundle.
- Part 1/2 answer labels may be valid even without spoken option transcripts. Group content is required for multi-question audio and multi-document passages.
- Client: render ordered content with async loading and deterministic grouping. Photo/passage/media errors must not be silently skipped.

## Critical session-state issue

Current design distinguishes `exam_sessions.state` SCHEDULED/IN_PROGRESS/CLOSED from `monitoring_attempts.state` ACTIVE. An ACTIVE attempt can be used for monitoring and audio READY before the proctor START. Backend MUST make a deliberate policy for when questions/save/submit become legal, and enforce it in all routes; a disabled JavaFX button is insufficient. Review existing QD-21: `deadlineAt` may start at writer takeover rather than proctor START. Do not silently move the clock. Make an explicit approved decision and regression tests before changing it.

## Multi-signal event contract (proposal, not final wire schema)

One candidate-provided event may have fields equivalent to:

```json
{
  "protocolVersion": "vN (to negotiate)",
  "type": "EXAM_ACTIVITY_EVENT",
  "messageId": "stable-across-retry",
  "requestId": "stable-request-id",
  "traceId": "correlation-id",
  "attemptId": "server-issued-active-attempt",
  "payload": {
    "eventId": "stable-event-id",
    "eventType": "USB_STORAGE_CONNECTED",
    "source": "WINDOWS_USB_STORAGE",
    "observedAt": "ISO-8601-client-time",
    "details": { "deviceClass": "USB_MASS_STORAGE" }
  }
}
```

**Do not implement this JSON verbatim before reviewing the existing message/DTO validator and approving a version/limit.** Server must record `receivedAt` separately. Device identity should be minimized and stable only as long as necessary to correlate connect/disconnect; avoid serial numbers, raw file paths or USB contents. Observe only when authorized exam monitoring is active and disclosed to candidates.

## Invariants to protect

- Server authenticates role and attempt scope before data or idempotency-cache access; no cross-user leak.
- `eventId` stable under retry; content conflict handled; accepted history event ACK only after database COMMIT. Duplicate event never creates a new history entry.
- `writerEpoch` and save `revision` preserve authority and monotonicity; stale writes cannot undo submission or timeout.
- Use the existing PostgreSQL locked-row + database clock boundary; do not use client wall clock to decide deadline.
- Monitoring current state (RAM, `syncEpoch`, sequence) is distinct from history events in DB. Restart/reconnect requires fresh FULL; stale epoch and DELTA base rejected. FULL never erases event/gap history.
- Heartbeat loss -> UNKNOWN, not cheating; absent observation is not proof of absence.
- Audio must pass checksum to READY, START must come from valid authoritative message, interrupted Listening stops/locks, reconnect never auto-replays or resumes.
- No scanner/network IO on JavaFX Application Thread; no observer started on proctor role; stop workers on logout, completion and exit.
- Monitoring is a best-effort observed signal from an ordinary client, not a tamper-proof anti-cheat kernel agent.

## USB sensor decision checklist

Candidate methods: Windows PnP device notifications vs bounded CIM/WMI query/polling. Compare hardware coverage, permissions, latency, event duplication, missed device arrival, JDK/JNA packaging and CPU cost. Emit categories for storage only; a USB mouse should not trigger a storage alert. Physical Windows tests are mandatory for PASS. No hidden screen capture, file inspection, filesystem mounting/ejecting or device blocking.
