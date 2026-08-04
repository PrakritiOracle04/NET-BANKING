# Phase 5 PRD — Operations, Compliance and Reporting

## 1. Purpose

Phase 5 adds an operational read layer around the banking platform.

Create:

- `audit-service` on port `8094`
- `report-service` on port `8095`
- `admin-service` on port `8096`

Extend only where required:

- domain-service event publication and read-only operational contracts
- API Gateway routes
- Notification Service report-event listener/template configuration
- root Maven, Compose, environment, and platform documentation

No Phase 5 service may execute a banking workflow, manipulate balances, approve KYC, change cards/loans, or write another service's business data.

## 2. Phase 4 prerequisite and development baseline

Phase 5 development begins only after Phase 4 is implemented, tested, documented, and merged into `main`.

Before creating the Phase 5 branch, verify:

- `loan-service` and `banking-scheduler-service` compile and run under Compose.
- Loan repayment, scheduled bill payment, EMI reminders, and overdue evaluation pass integration tests.
- Phase 4 public/internal APIs and Kafka event payloads are documented and stable.
- Phase 1–4 clean-schema and regression tests pass.
- `main` contains the final Phase 4 Maven, Compose, Gateway, Notification, environment, and documentation changes.

Create the Phase 5 branch from that post-Phase-4 `main` commit. Record that commit as the Phase 5 baseline.

### Service-isolation rules

- Phase 5 must not import Phase 4 Java classes or share Phase 4 entities, repositories, or DTO modules.
- Loan and Scheduler clients use Phase 5-owned HTTP DTO records and configurable URLs.
- Phase 5 integrates against the actual documented Phase 4 REST/event contracts, not assumptions or temporary mocks.
- Required missing operational-read endpoints or audit fields may be added through small Phase 5 extensions without redesigning Phase 4 business logic.
- Cross-service payloads remain documented JSON/OpenAPI contracts, not shared Java source.

### Reserved ports

| Phase | Service | Port |
| --- | --- | ---: |
| 4 | Loan | `8092` |
| 4 | Banking Scheduler | `8093` |
| 5 | Audit | `8094` |
| 5 | Report | `8095` |
| 5 | Admin | `8096` |

### Shared platform files

Phase 5 extends the already-merged Phase 4 versions of `pom.xml`, `compose.yaml`, `Containerfile`, Gateway routes, Notification configuration, environment documentation, README, and API documentation.

Preserve all Phase 4 modules, routes, ports, topics, templates, pool settings, and documentation while adding Phase 5. Notification additions should use concern-specific listener/template classes so Loan/Scheduler and Report notifications remain readable and independently testable.

## 3. Architecture

Audit, Report, Admin, and Notification are parallel consumers of platform information. They do not form a synchronous chain.

```text
Domain services -> Kafka -> Audit Service

Client -> Gateway -> Admin Service -> read-only domain/Audit APIs

Client -> Gateway -> Report Service -> read-only domain APIs
                                  -> file generation
                                  -> Kafka report-generated
                                  -> Notification Service
```

Audit Service is never placed in the success path of a banking operation. A temporary Audit outage must not fail a deposit, transfer, repayment, or scheduled payment.

## 4. Permitted existing-service changes

The original instruction “do not modify existing services” is narrowed as follows.

Permitted:

- publishing missing sanitized audit events after business outcomes are stable
- adding read-only `/internal/operations/**` endpoints required for Admin/Report aggregation
- adding pagination, filtering, summary, and count DTOs without changing existing public behavior
- adding `report-generated` handling to the existing Notification Service

Forbidden:

- changing workflow ordering or compensation
- moving domain logic into Admin, Report, or Audit
- exposing repositories/entities across modules
- adding cross-service database access
- routing internal operational endpoints through Gateway
- changing existing public response contracts without a separate approved migration

## 5. Audit Service

### Purpose

Audit Service maintains an append-only, sanitized record of covered platform events from deployment onward. It cannot promise a record for an operation whose producer never emitted an event or whose event expired before consumption.

### Ownership

Owns:

- `AUDIT_LOGS`
- `AUDIT_CONSUMER_FAILURES` for dead-letter/reprocessing metadata, if implemented

No public create, update, or delete audit endpoint exists.

### Audit record fields

- `auditId` — generated UUID string
- `eventId` — required producer identifier
- `eventVersion`
- `eventType`
- `occurredAt`
- `ingestedAt`
- `actorUserId` — nullable for system operations
- `actorRole`
- `sourceService`
- `action`
- `entityType`
- `referenceId`
- `correlationId`
- `status`
- `severity`
- `sanitizedMetadata` — bounded JSON without secrets or raw request bodies
- `topic`, `partition`, `offset`

`eventId` must be unique. Kafka topic/partition/offset should also be uniquely constrained as a secondary deduplication guard.

### Immutability

- Audit application code supports inserts and reads only.
- Records are never edited or physically deleted through the application.
- Consumer redelivery returns the existing audit record instead of inserting a duplicate.
- Phase 5 defines no retention deletion. Long-term archival/retention policy is a future operational decision.

### Event envelope

New or extended producers use this logical envelope:

```json
{
  "eventId": "uuid",
  "eventVersion": 1,
  "eventType": "bill-payment-success",
  "occurredAt": "2026-09-01T10:15:30Z",
  "actorUserId": "user-id",
  "actorRole": "CUSTOMER",
  "sourceService": "banking-workflow-service",
  "action": "BILL_PAYMENT_COMPLETED",
  "entityType": "BILL_PAYMENT",
  "referenceId": "workflow-reference",
  "correlationId": "workflow-reference",
  "status": "SUCCESS",
  "severity": "INFO",
  "metadata": {
    "accountId": "account-id"
  }
}
```

Do not include passwords, JWTs, internal API keys, OTPs, Aadhaar, PAN, full card numbers, complete account numbers, email bodies, or unrestricted exception messages.

Audit Service provides topic-specific adapters for already-existing Phase 1–3B events that do not yet use this complete envelope. Do not break Notification Service's current payloads merely to satisfy Audit.

### Covered event catalog

Phase 5 must inventory and test the exact configured topics. Minimum coverage:

- registration and authentication success/failure/logout
- account opening, debit, and credit outcomes
- transaction creation/reversal outcomes
- transfer, deposit, withdrawal, and bill-payment outcomes
- card issue/activation/block/unblock/limit changes
- beneficiary and KYC administrative changes
- report requested/completed/failed/downloaded
- administrative sensitive searches/actions
- Phase 4 loan registration/repayment/reminder/overdue events
- Phase 4 schedule lifecycle/execution events

Missing producer events require small producer extensions; they cannot be inferred reliably by Audit Service.

### Kafka reliability

- Use a dedicated Audit consumer group.
- Process with at-least-once semantics.
- Disable automatic acknowledgement until the audit insert commits.
- Retry transient failures with bounded backoff.
- Route poison/unparseable messages to an audit dead-letter topic and record diagnostic metadata without secrets.
- A duplicate event is a successful no-op.
- Timeline order uses `occurredAt`, then `ingestedAt`; no global ordering across Kafka topics is promised.

### Audit APIs

All routes require `ADMIN`.

| Method and route | Purpose |
| --- | --- |
| `GET /api/audit` | Paginated search using optional filters |
| `GET /api/audit/{id}` | Audit detail |
| `GET /api/audit/users/{userId}` | Paginated user timeline |
| `GET /api/audit/timeline` | Time-ordered filtered timeline |
| `GET /api/audit/summary` | Counts by type/status/severity for a bounded date range |

Filters include date range, actor user ID, action, source service, entity type, reference, correlation ID, status, severity, page, size, sort field, and direction. Whitelist sortable fields and cap page size/date range.

`/api/audit/search` is removed because it duplicates filtered `GET /api/audit`.

## 6. Report Service

### Ownership

Owns:

- `REPORT_JOBS`
- `GENERATED_REPORTS`

Local foreign key:

- `GENERATED_REPORTS.REPORT_JOB_ID -> REPORT_JOBS.REPORT_JOB_ID`

### Job statuses

- `QUEUED`
- `RUNNING`
- `COMPLETED`
- `FAILED`
- `EXPIRED`

### Supported reports

- Account statement
- Transaction report
- Customer report
- Card report
- Loan report when Loan Service is available
- Administrative overview report
- Audit report for administrators

### Generation model

Report creation is asynchronous:

1. Authenticate and authorize the request.
2. Validate ownership/role while the JWT is present.
3. Store a sanitized immutable request snapshot and requester user ID/role.
4. Return `202 Accepted` with a report job ID.
5. A background worker claims the job.
6. The worker calls read-only internal domain contracts with `X-Internal-Api-Key`; it never stores or reuses the JWT.
7. Generate PDF or CSV to an isolated configured report-storage volume.
8. Persist file metadata, checksum, size, content type, and expiry.
9. Mark the job complete and publish `report-generated` after commit.
10. Notification Service emails a readiness message containing a report/UI reference, not an unauthenticated direct file URL.

Generated files are not stored inside the container image. File paths are never accepted from clients and never returned as server filesystem paths.

### Report APIs

| Method and route | Access | Behavior |
| --- | --- | --- |
| `POST /api/reports/account-statements` | owner/ADMIN | Queue statement; `202` |
| `POST /api/reports/transactions` | CUSTOMER/ADMIN | Queue scoped transaction report; `202` |
| `POST /api/reports/customers` | ADMIN | Queue customer report; `202` |
| `POST /api/reports/cards` | owner/ADMIN | Queue masked card report; `202` |
| `POST /api/reports/loans` | owner/ADMIN | Queue loan report; `202` |
| `POST /api/reports/admin-overview` | ADMIN | Queue operational overview; `202` |
| `POST /api/reports/audit` | ADMIN | Queue audit report; `202` |
| `GET /api/reports/{id}` | requester/ADMIN | Job and generated-file metadata |
| `GET /api/reports/history` | JWT | Requester's history; ADMIN may filter requester |
| `GET /api/reports/{id}/download` | requester/ADMIN | Authenticated binary download |

Binary download is the intentional exception to the JSON `ApiResponse` format. It returns the correct `Content-Type`, safe `Content-Disposition`, content length, and file bytes. Missing/expired reports return the normal JSON error body.

### Formats and safety

- Support PDF and UTF-8 CSV.
- Use a fixed template and bounded fields for PDFs.
- Escape CSV fields and neutralize formula injection for values beginning with `=`, `+`, `-`, or `@`.
- Never display full PAN/card number, Aadhaar, PAN identity number, secrets, or password data.
- Define configurable maximum rows, date range, file size, job timeout, and retention period.
- Validate ownership again when downloading.
- Expired metadata remains; expired file content may be deleted by Report Service according to configured retention.

### Data consistency and failures

Reports are point-in-time best efforts across service APIs, not distributed database snapshots. Include `generatedAt` and source timestamps. A required-source timeout fails the job; it must not silently produce a misleading partial financial report.

## 7. Admin Service

### Purpose

Admin Service is a stateless, read-only aggregation facade. Domain mutations continue through their owning service or Workflow; Admin Service does not proxy balance, card, loan, KYC, or schedule mutations in Phase 5.

Admin Service owns no database tables.

### APIs

All routes require `ADMIN`.

| Method and route | Purpose |
| --- | --- |
| `GET /api/admin/dashboard` | Aggregated operational dashboard |
| `GET /api/admin/customers` | Paginated customer monitoring/search |
| `GET /api/admin/accounts` | Paginated account monitoring/search |
| `GET /api/admin/transactions` | Paginated transaction monitoring/search |
| `GET /api/admin/loans` | Loan monitoring/search |
| `GET /api/admin/cards` | Masked card monitoring/search |
| `GET /api/admin/branches` | Branch monitoring/search |
| `GET /api/admin/bill-payments` | Bill-payment monitoring/search |
| `GET /api/admin/schedules` | Scheduler monitoring/search |
| `GET /api/admin/audit-summary` | Audit counts/recent events |
| `GET /api/admin/system` | Application health aggregation only |
| `GET /api/admin/search` | Bounded global search |

“System monitoring” means aggregating configured service health/readiness and last successful response. Metrics, tracing, alerting infrastructure, logs aggregation, and host/container monitoring remain out of scope.

### Aggregation behavior

- Use configurable per-service connection/read timeouts.
- Execute independent dashboard calls concurrently with a bounded executor.
- Never create an unbounded fan-out.
- Return section-level status: `AVAILABLE`, `UNAVAILABLE`, or `DEGRADED`.
- Dashboard may return `200` with explicit partial-section errors when optional/read-only sources are unavailable.
- Return `503` only when the Admin Service itself cannot provide a meaningful response or all required core sources fail.
- Include `generatedAt` and optional per-section `asOf` timestamps.
- Apply short configurable caching only to counts/health summaries, never to sensitive search results.

Global search must cap result counts per entity and return typed groups. Search card data only by masked last four digits; never accept or expose a full card number.

## 8. Read-only operational contracts

Admin and background Report jobs require direct internal REST contracts because background jobs cannot safely retain customer JWTs.

Each owning service may expose narrowly scoped endpoints such as:

```http
GET /internal/operations/customers/summary
GET /internal/operations/accounts/summary
GET /internal/operations/transactions/search
GET /internal/operations/cards/summary
GET /internal/operations/bill-payments/summary
GET /internal/operations/loans/summary
GET /internal/operations/schedules/summary
```

Rules:

- Require `X-Internal-Api-Key`.
- Do not route through Gateway.
- Accept explicit requester/owner scope where needed.
- Return DTOs only, with pagination and whitelisted filters/sorts.
- Do not expose sensitive encrypted fields or repositories.
- Do not mutate domain data.
- Loan/Scheduler operational contracts must be verified against the completed Phase 4 implementation before Phase 5 coding begins.

## 9. Kafka and Notification

Audit Service consumes covered banking/audit topics independently of Notification Service.

Report Service publishes:

- `report-generated`
- `report-failed`

Admin Service may publish sanitized `admin-action` events for sensitive searches and report requests. It does not publish one event for every dashboard refresh unless required by policy.

Extend the existing Notification Service with a separate report-event listener/template path. Do not combine report file access with SMTP attachment delivery in Phase 5. Notification events contain recipient, report reference, report type, and readiness/failure message, but no filesystem path or financial report contents.

## 10. Security and privacy

- JWT and `ADMIN` are mandatory for Audit and Admin APIs.
- Report authorization follows the route matrix; customers can generate/download only their own reports.
- Internal operational routes require the internal API key and are absent from Gateway.
- Customer identity comes from JWT at report request time, never from a customer-controlled user-ID field.
- Store requester ID and authorization decision, not the JWT.
- Audit and report metadata must be sanitized and size bounded.
- Never log or report passwords, JWTs, API keys, OTPs, Aadhaar, PAN identity values, full card numbers, card encryption material, or unrestricted email bodies.
- Use immutable user IDs instead of usernames for joins and filters.

## 11. Response and status rules

Use the platform success/error envelopes for JSON APIs.

| Situation | Status |
| --- | ---: |
| Successful read/search | `200` |
| Report accepted | `202` |
| Binary report download | `200` |
| Invalid filter, range, format, or pagination | `400` |
| Missing/invalid JWT | `401` |
| Wrong role/ownership/internal key | `403` |
| Audit/report resource not found | `404` |
| Duplicate incompatible report idempotency request | `409` |
| Report expired | `410` |
| Required downstream unavailable | `503` |

Use bounded pagination everywhere. Never return an error status with success data.

## 12. Database constraints and indexes

Every owned table has a primary key using the platform's UUID-string convention.

Audit indexes should cover:

- `(OCCURRED_AT)`
- `(ACTOR_USER_ID, OCCURRED_AT)`
- `(EVENT_TYPE, OCCURRED_AT)`
- `(SOURCE_SERVICE, OCCURRED_AT)`
- `(REFERENCE_ID)`
- `(CORRELATION_ID)`
- `(STATUS, SEVERITY, OCCURRED_AT)` where supported by query design

Report indexes should cover requester/date, status/date, and expiry. Enforce unique job idempotency where supplied and a one-to-one generated-report/job relationship.

Hibernate entities remain the DDL source of truth. No cross-service foreign keys are created even when local development uses one Oracle schema.

## 13. Out of scope

- Oracle Select AI or Vector Search
- AI assistant, recommendations, semantic search, analytics, or budgeting
- Fraud-detection intelligence
- Mutation-oriented administrative workflows
- Data warehouse or cross-service reporting database
- Prometheus/Grafana, distributed tracing, centralized log aggregation, or infrastructure alerting
- Public unauthenticated or signed report links
- Emailing report files as attachments
- Excel generation
- Compliance retention/legal-hold automation
- A transactional outbox retrofit across every existing producer

Without a transactional outbox in each producing service, Phase 5 provides reliable idempotent consumption of events successfully delivered to Kafka, but it is not a legally guaranteed zero-loss compliance ledger. A future compliance-hardening phase may add producer outboxes without changing the Audit Service API or record model.

## 14. Testing

### Audit

- Every configured topic adapter persists a correctly normalized record.
- Duplicate event and duplicate Kafka delivery create one audit row.
- Invalid messages retry and reach dead-letter handling.
- Consumer restart resumes without loss or duplicate rows.
- Search filters, whitelisted sorting, pagination, timeline, and summary work.
- No update/delete API exists and repository use is append-only.
- Sensitive fields are absent from stored metadata and responses.

### Reports

- PDF and CSV content, headers, checksums, and safe filenames.
- CSV escaping and formula-injection protection.
- Customer ownership and ADMIN access.
- Asynchronous transitions: queued, running, completed, failed, expired.
- Required-source timeout fails rather than producing misleading partial output.
- Download requires authorization and expired content returns `410`.
- `report-generated`/`report-failed` are consumed by Notification Service.
- Container restart preserves files through the configured volume.

### Admin

- ADMIN-only access.
- Dashboard totals and recent sections match source APIs.
- Bounded parallel fan-out and timeout behavior.
- Partial response marks any temporarily unavailable runtime source explicitly.
- Global search grouping, pagination limits, masking, and sort validation.
- System endpoint reports application health only.

### Phase 4 dependency and Phase 1–5 integration

- Phase 5 Loan/Scheduler clients match the completed Phase 4 contracts.
- Loan and Scheduler monitoring/reporting work with the real Phase 4 services.
- Kafka Audit adapters accept the final Phase 4 event versions.
- Notification listeners for both phases coexist without duplicate emails.
- Combined `pom.xml`, Compose, Gateway, Containerfile, environment, and docs contain all services and ports.
- All services start against a clean Oracle schema without exhausting Oracle connection limits.
- Existing registration, 2FA, onboarding, account, transfer, bill payment, card, Saga compensation, Loan, and Scheduler regressions remain green.

## 15. Acceptance criteria

Phase 5 is complete only when:

- Audit, Report, and Admin services compile, start, and expose health endpoints.
- Audit records every event in the agreed covered-event catalog from available producers.
- Audit ingestion is idempotent, searchable, append-only, and resilient to poison messages.
- PDF and CSV reports are generated asynchronously and downloaded securely.
- Report ownership, masking, expiry, storage, and notification rules work.
- Admin dashboard/search aggregate through bounded read-only REST calls.
- Loan and Scheduler dashboard, search, report, and audit integrations work against completed Phase 4 services.
- No Phase 5 service reads another service's tables or mutates banking data.
- Internal operational endpoints are unavailable through Gateway.
- JWT/RBAC and internal-key rules are enforced with correct status codes.
- Phase 5 shared-file changes preserve all Phase 4 routes, services, topics, templates, and documentation.
- Swagger and root documentation describe public/internal APIs, event versions, ownership, download behavior, and degraded responses.
- Clean-schema, Kafka, container restart, failure, security, and complete regression tests pass.

## 16. Implementation constraints for Codex

- Create the Phase 5 branch only from `main` after Phase 4 is complete and merged.
- Build Audit, Report, and Admin as independently compilable modules first.
- Keep all external-service DTOs local to their consuming Phase 5 module.
- Verify Phase 4 contracts first; add only the missing read-only operational endpoints or audit fields required by the approved checklist.
- Implement one bounded capability at a time, test it, and use short descriptive commits without task numbers.
- Do not commit `.env`, generated reports, report volumes, target folders, or secrets.
- Do not push until explicitly instructed.
- Run the complete Phase 1–5 clean-schema and regression suite before pushing the Phase 5 branch.
