# Phase 5 Operations Guide

## Architecture

Phase 5 adds three independent services:

- Audit Service (`8094`) consumes domain Kafka events and stores immutable sanitized records.
- Report Service (`8095`) queues asynchronous report jobs, reads bounded internal APIs, and generates CSV/PDF files.
- Admin Service (`8096`) provides read-only operational aggregation and owns no tables.

Public traffic always follows:

```text
Client -> API Gateway :8080 -> Auth session validation -> Audit / Report / Admin
```

Background and administrative reads follow:

```text
Report/Admin -> /internal/operations/** -> owning domain service
             -> X-Internal-Api-Key
```

Internal routes are deliberately absent from Gateway. Report workers do not retain customer JWTs; the immutable requester ID and role are stored when a report is queued.

## Data ownership

| Service | Owned tables |
| --- | --- |
| Audit | `AUDIT_LOGS`, `AUDIT_CONSUMER_FAILURES` |
| Report | `REPORT_JOBS`, `GENERATED_REPORTS` |
| Admin | None |

All local services currently share one Oracle schema, but no Phase 5 repository maps another service's table. Cross-service references are logical identifiers, not cross-service database relationships.

## Audit behavior

Audit consumes existing notification-compatible events and the Phase 5 lifecycle topics documented in `PHASE5_EVENT_CATALOG.md`.

- Consumer acknowledgement occurs only after the Audit insert commits.
- `eventId` is unique. Legacy events derive it from topic, partition, and offset.
- Kafka coordinates are independently unique, making redelivery a successful no-op.
- Topic-specific adapters normalize legacy payloads.
- Recipient addresses, tokens, secrets, OTPs, Aadhaar, PAN, full card/account numbers, and message bodies are not stored.
- Poison messages receive bounded retries, are published to `audit-events-dlt`, and create bounded metadata in `AUDIT_CONSUMER_FAILURES` without storing the raw payload.
- Audit routes have no create, update, or delete operation.

## Report lifecycle

```text
QUEUED -> RUNNING -> COMPLETED -> EXPIRED
                  -> FAILED
```

1. A JWT-authenticated request is scoped to its requester and stored without the JWT.
2. Optional `Idempotency-Key` reuse returns the original equivalent job; incompatible reuse returns `409`.
3. A worker claims a queued row with a database lock.
4. It calls bounded `/internal/operations/**` contracts with the internal API key.
5. It creates UTF-8 CSV or fixed-template PDF content under the configured report volume.
6. It stores checksum, size, content type, row count, generation time, and expiry.
7. Only after the database transaction commits does it publish `report-generated` or terminal `report-failed`.
8. Notification resolves the user's current Auth email and sends a readiness/failure message. No report attachment or unauthenticated file URL is emailed.
9. Download checks requester ownership again and emits `report-downloaded`.
10. Expiry removes content, retains metadata, marks the job `EXPIRED`, and emits `report-expired`.

CSV values beginning with `=`, `+`, `-`, or `@` are prefixed to prevent spreadsheet formula execution. File names and paths are generated internally; clients cannot provide them.

## Admin behavior

- All APIs require `ADMIN`.
- Independent dashboard and health calls run on a bounded executor.
- Each section reports `AVAILABLE`, `DEGRADED`, or `UNAVAILABLE` with an `asOf` time.
- Short caching applies only to summaries and health; search responses are never cached.
- Global search caps each typed result group. Card search accepts only one to four digits and operates on masked output.
- A global search publishes `admin-sensitive-action` without placing the search text in Kafka metadata.
- Admin never changes balances, KYC, beneficiaries, cards, loans, schedules, or Saga state.

## Runtime configuration

The ignored root `.env` is the runtime source of truth. Phase 5 adds:

- `AUDIT_DB_URL`, `AUDIT_DB_USERNAME`, `AUDIT_DB_PASSWORD`
- `REPORT_DB_URL`, `REPORT_DB_USERNAME`, `REPORT_DB_PASSWORD`
- `AUDIT_SERVICE_URL`, `REPORT_SERVICE_URL`, `ADMIN_SERVICE_URL`
- `AUDIT_KAFKA_RETRY_COUNT`, `AUDIT_KAFKA_RETRY_BACKOFF_MS`, `AUDIT_METADATA_MAX_LENGTH`, `AUDIT_MAX_QUERY_RANGE_DAYS`
- `REPORT_STORAGE_PATH`, `REPORT_RETENTION_HOURS`, `REPORT_MAX_ROWS`, `REPORT_MAX_RANGE_DAYS`, `REPORT_MAX_FILE_SIZE_BYTES`
- `REPORT_CLIENT_CONNECT_TIMEOUT_MS`, `REPORT_CLIENT_READ_TIMEOUT_MS`, `REPORT_WORKER_POLL_DELAY_MS`, `REPORT_WORKER_RECOVERY_DELAY_MS`, `REPORT_JOB_TIMEOUT_MINUTES`
- `ADMIN_CLIENT_CONNECT_TIMEOUT_MS`, `ADMIN_CLIENT_READ_TIMEOUT_MS`, `ADMIN_EXECUTOR_THREADS`, `ADMIN_SUMMARY_CACHE_SECONDS`, `ADMIN_MAX_PAGE_SIZE`, `ADMIN_GLOBAL_SEARCH_GROUP_SIZE`

Kafka topic names remain structural service configuration in YAML. `Containerfile` is unchanged; the existing generic `SERVICE` build argument packages all three Phase 5 services.

The report volume is `net-banking-report-data`. Removing or recreating that volume deletes generated files but not Oracle report metadata.

## Local verification

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd test
podman compose config --quiet
podman compose up -d --build
podman compose ps
```

Use Gateway for all public tests. Use Kafka UI at `http://localhost:8081` to inspect domain, report, and Audit DLT topics. For email verification, register/login using the configured SMTP sender address as the test user's email so readiness messages are visible in the same mailbox.

Useful checks:

- A CUSTOMER receives `403` for `/api/audit/**`, `/api/admin/**`, and administrator-only report types.
- Reusing an equivalent report idempotency key returns the original job.
- Reusing it with changed filters or format returns `409`.
- A report file is downloadable only by its requester or an ADMIN.
- Domain operations still succeed if Audit is unavailable.
- Duplicate Kafka delivery creates one Audit row.
- Audit metadata contains no notification recipient or sensitive identity/payment value.
- Admin dashboard identifies an unavailable service by section instead of hiding partial failure.
