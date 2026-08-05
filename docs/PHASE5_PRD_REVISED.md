# Phase 5 PRD — Operations, Compliance and Reporting

**Revision:** Post-Phase-4 implementation baseline

**Baseline commit:** `70b9cb7` (`main`, merge of PR #13)

**Baseline date:** 2026-08-05

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

Phase 5 is built on the completed platform through Phase 4, including:

- Gateway-enforced revocable JWT sessions using the `sid` claim
- logical service ownership while all services currently share one Oracle database/schema
- Saga-based account opening, transfer, deposit, withdrawal, bill payment, scheduled bill payment, and loan repayment
- card lifecycle events
- Loan Service with `HOME`, `VEHICLE`, `PERSONAL`, `EDUCATION`, and `BUSINESS` loan types
- Loan events including `loanType` in the `loan-created` payload
- Banking Scheduler execution, retry, EMI-reminder, and overdue-maintenance flows
- Kafka and Kafbat Kafka UI in the existing Compose stack

## 2. Phase 4 prerequisite and development baseline

Phase 4 is merged into `main`. Phase 5 must branch from baseline commit `70b9cb7` or a later approved `main` commit that contains it.

Before creating the Phase 5 branch, record and verify:

- `loan-service` and `banking-scheduler-service` compile and run under Compose.
- Loan repayment, scheduled bill payment, EMI reminders, and overdue evaluation pass integration tests.
- Phase 4 public/internal APIs and Kafka event payloads are documented and stable, including loan type.
- Phase 1–4 clean-schema and regression tests pass.
- `main` contains the final Phase 4 Maven, Compose, Gateway, Notification, environment, and documentation changes.
- Protected Gateway requests validate the JWT `sid` through Auth Service and fail closed when session validation is unavailable.

Create the Phase 5 branch from that post-Phase-4 `main` commit. Record that commit as the Phase 5 baseline.

The merged baseline contains repeated identical keys in `banking-scheduler-service/src/main/resources/application.yml`. Remove only those duplicates as the first non-functional Phase 5 cleanup, retain the same values/placeholders, compile Scheduler, and confirm its container still starts. Do not combine that cleanup with unrelated configuration redesign.

### Verified platform baseline

| Concern | Phase 5 baseline |
| --- | --- |
| Public entry point | API Gateway on `8080` |
| Authentication | Signed JWT containing immutable user ID in `sub`, roles, and revocable session ID in `sid` |
| Session enforcement | Gateway calls `POST /internal/auth/sessions/validate` before routing protected requests |
| Internal trust | `X-Internal-Api-Key`; internal routes are not exposed through Gateway |
| Database | One Oracle instance/schema in local development with strict logical table ownership per service |
| Identifier representation | UUID values stored as `VARCHAR2(36)` strings |
| DDL source | Service-owned Hibernate entities using the existing JPA schema-update convention |
| Synchronous integration | Direct internal REST calls over Compose DNS names |
| Asynchronous integration | Kafka at `kafka:29092` inside Compose; `localhost:9092` from the host |
| Kafka inspection | Kafbat Kafka UI at `localhost:8081` |
| Container exposure | Gateway and infrastructure ports only; application service ports remain internal to Compose |

### Service-isolation rules

- Phase 5 must not import Phase 4 Java classes or share Phase 4 entities, repositories, or DTO modules.
- Every Phase 5 client uses Phase 5-owned HTTP DTO records and configurable service URLs.
- Phase 5 integrates against the actual documented Phase 4 REST/event contracts, not assumptions or temporary mocks.
- Required missing operational-read endpoints or audit fields may be added through small Phase 5 extensions without redesigning Phase 4 business logic.
- Cross-service payloads remain documented JSON/OpenAPI contracts, not shared Java source.
- No Phase 5 repository may map or query another service's table even though local development uses one physical Oracle schema.

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

The existing generic `Containerfile` already builds a service selected through the `SERVICE` build argument. Phase 5 reuses it. Do not redesign the container build, add a `service-defaults` abstraction, or change the generic image flow unless a proven Phase 5 build failure requires an approved change.

Runtime configuration remains sourced from root `.env` through Compose and service YAML placeholders without fallback values. Kafka topic names may remain explicit application configuration and do not need to be moved into `.env`. Secrets, credentials, database URLs, service URLs, limits, timeouts, schedules, storage paths, and pool sizes must not be hardcoded in Java.

## 3. Architecture

Audit, Report, Admin, and Notification are parallel consumers of platform information. They do not form a synchronous chain.

```text
Client -> Gateway -> Auth session validation -> Audit/Admin/Report public API

Domain services -> Kafka -> Audit Service

Client -> Gateway -> Admin Service -> read-only domain/Audit APIs

Client -> Gateway -> Report Service -> read-only domain APIs
                                  -> file generation
                                  -> Kafka report-generated
                                  -> Notification Service
```

Audit Service is never placed in the success path of a banking operation. A temporary Audit outage must not fail a deposit, transfer, repayment, or scheduled payment.

Gateway is the online session-revocation enforcement point. It validates `sid` through Auth before forwarding any protected Phase 5 request. Audit, Admin, and Report still verify JWT signature, expiry, subject, and roles locally. Background report workers never reuse a captured JWT; they call only internal operational endpoints with the internal API key.

## 4. Permitted existing-service changes

The original instruction “do not modify existing services” is narrowed as follows.

Permitted:

- enriching existing Kafka payloads additively with stable actor/reference/operation fields needed for audit
- making existing domain-event publication independent of notification-recipient lookup so an unavailable email address does not suppress an auditable business event
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
- making Audit, Admin, or Report call another service's database
- forwarding or persisting a customer JWT for background work

## 5. Audit Service

### Purpose

Audit Service maintains an append-only, sanitized record of covered platform events from deployment onward. It cannot promise a record for an operation whose producer never emitted an event or whose event expired before consumption.

### Ownership

Owns:

- `AUDIT_LOGS`
- `AUDIT_CONSUMER_FAILURES` for dead-letter/reprocessing metadata

No public create, update, or delete audit endpoint exists.

### Audit record fields

- `auditId` — generated UUID string
- `eventId` — normalized unique identifier used by Audit Service
- `producerEventId` — nullable identifier supplied by a producer
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

`eventId` must be unique. For the new canonical envelope, it is the producer's `eventId`. For a legacy payload without one, Audit derives a deterministic identifier from Kafka topic, partition, and offset. The `(TOPIC, PARTITION, OFFSET)` tuple is also uniquely constrained. Audit must not call a domain service during Kafka ingestion merely to reconstruct missing identity.

### Immutability

- Audit application code supports inserts and reads only.
- Records are never edited or physically deleted through the application.
- Consumer redelivery returns the existing audit record instead of inserting a duplicate.
- Phase 5 defines no retention deletion. Long-term archival/retention policy is a future operational decision.

### Canonical event envelope

New Phase 5 producers and newly introduced domain events use this logical envelope. Existing notification-oriented events are adapted without breaking their consumers.

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

Audit Service provides topic-specific adapters for already-existing Phase 1–4 events that do not yet use this complete envelope. Do not break Notification Service's current payloads merely to satisfy Audit.

Additive fields may be added to an existing payload. Existing field names, topic names, meanings, and Notification-required variables must remain compatible.

### Existing topic inventory

The merged Phase 4 baseline already publishes the following topics:

| Producer | Existing topics |
| --- | --- |
| Auth Service | `registration-success`, `login-alert` |
| Banking Workflow Service | `transaction-created`, `account-debited`, `account-credited`, `bill-payment-success`, `bill-payment-failed`, `loan-payment-success`, `loan-payment-failed` |
| Card Service | `card-issued`, `card-activated`, `card-blocked`, `card-unblocked`, `card-limit-updated` |
| Loan Service | `loan-created`, `emi-reminder`, `loan-overdue` |
| Banking Scheduler Service | `schedule-triggered`, `schedule-completed`, `schedule-failed` |

`loan-created` includes the selected `loanType`. Audit stores that value only as sanitized metadata; loan reporting obtains authoritative current state through Loan Service's operational API.

The baseline payloads were designed partly for email notification and are not uniform. Audit therefore requires topic-specific adapters. Adapters must ignore `recipient`, email addresses, message bodies, and other notification-only PII rather than storing the complete payload.

Before Audit is accepted, the existing producers must receive only the following bounded compatibility changes where the information is currently missing:

- publish a domain event even when notification-recipient resolution fails; `recipient` may be absent and Notification may skip delivery
- add immutable actor/customer user ID where the producer knows it
- add `occurredAt`, outcome status, source operation/workflow type, and stable domain reference where missing
- retain the existing topic and Notification fields
- never add JWTs, secrets, complete account numbers, encrypted identity fields, or unrestricted error text

Do not retrofit the complete canonical envelope into every old DTO merely for visual uniformity. Audit adapters normalize the variations.

### Covered event catalog

Phase 5 audit coverage is:

- registration, login success, authentication failure, logout, and logout-all
- account opening, debit, credit, reversal/compensation, and status changes
- transaction creation and reversal outcomes
- transfer, deposit, withdrawal, bill-payment, and loan-repayment outcomes, including compensated failures
- card issue, activation, block, unblock, and limit changes
- beneficiary create/update/delete/status changes
- KYC submission and administrative KYC-status changes without Aadhaar/PAN values
- loan registration with loan type, repayment, reminder, overdue, and status changes
- schedule create/update/pause/resume/cancel plus trigger/completion/final-failure outcomes
- report requested/completed/failed/downloaded/expired
- administrative sensitive searches and report requests

New topics should be concern-specific and minimal. Phase 5 must document a final producer/topic/event-type matrix before implementation. Notification Service subscribes only to events that require email; Audit Service may consume the wider catalog independently.

Missing producer events require small producer extensions; they cannot be inferred reliably by Audit Service.

### Kafka reliability

- Use a dedicated Audit consumer group.
- Process with at-least-once semantics.
- Disable automatic acknowledgement until the audit insert commits.
- Retry transient failures with bounded backoff.
- Route poison/unparseable messages to an Audit-owned dead-letter topic and persist bounded failure metadata without the raw sensitive payload.
- A duplicate event is a successful no-op.
- Timeline order uses `occurredAt`, then `ingestedAt`; no global ordering across Kafka topics is promised.
- A failure to persist Audit data must never acknowledge the original Kafka record as successfully processed.

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

Jobs are claimed using an atomic state transition from `QUEUED` to `RUNNING`. A restarted worker may recover a stale `RUNNING` job according to the configured job timeout. Generation retries must not create multiple generated-file rows or duplicate completion notifications.

### Supported reports

- Account statement
- Transaction report
- Customer report
- Card report
- Loan report including loan type, status, principal, outstanding balance, EMI, and dates
- Bill-payment report
- Scheduled-payment and execution report
- Administrative overview report
- Audit report for administrators

### Generation model

Report creation is asynchronous:

1. Authenticate and authorize the request.
2. Validate ownership/role while the JWT is present.
3. Store a sanitized immutable request snapshot, requester user ID/role, and optional `Idempotency-Key`; never store the JWT.
4. Return `202 Accepted` with a report job ID.
5. A background worker claims the job.
6. The worker calls read-only internal domain contracts with `X-Internal-Api-Key`; it never stores or reuses the JWT.
7. Generate PDF or CSV to an isolated configured report-storage volume.
8. Persist file metadata, checksum, size, content type, and expiry.
9. Mark the job complete and publish `report-generated` after commit. Publish `report-failed` only after failure state commits.
10. Resolve the current notification recipient through Auth's internal recipient contract and let Notification Service email a readiness/failure message containing a report/UI reference, not an unauthenticated direct file URL.

Generated files are not stored inside the container image. File paths are never accepted from clients and never returned as server filesystem paths.

### Report APIs

| Method and route | Access | Behavior |
| --- | --- | --- |
| `POST /api/reports/account-statements` | owner/ADMIN | Queue statement; `202` |
| `POST /api/reports/transactions` | CUSTOMER/ADMIN | Queue scoped transaction report; `202` |
| `POST /api/reports/customers` | ADMIN | Queue customer report; `202` |
| `POST /api/reports/cards` | owner/ADMIN | Queue masked card report; `202` |
| `POST /api/reports/loans` | owner/ADMIN | Queue loan report; `202` |
| `POST /api/reports/bill-payments` | owner/ADMIN | Queue bill-payment report; `202` |
| `POST /api/reports/schedules` | owner/ADMIN | Queue scheduled-payment/execution report; `202` |
| `POST /api/reports/admin-overview` | ADMIN | Queue operational overview; `202` |
| `POST /api/reports/audit` | ADMIN | Queue audit report; `202` |
| `GET /api/reports/{id}` | requester/ADMIN | Job and generated-file metadata |
| `GET /api/reports/history` | JWT | Requester's history; ADMIN may filter requester |
| `GET /api/reports/{id}/download` | requester/ADMIN | Authenticated binary download |

Binary download is the intentional exception to the JSON `ApiResponse` format. It returns the correct `Content-Type`, safe `Content-Disposition`, content length, and file bytes. Missing/expired reports return the normal JSON error body.

Report-creation routes accept an optional `Idempotency-Key`. Reusing the same key with an equivalent requester, report type, format, and filter snapshot returns the original job. Reusing it with an incompatible request returns `409`.

### Formats and safety

- Support PDF and UTF-8 CSV.
- Use a fixed template and bounded fields for PDFs.
- Escape CSV fields and neutralize formula injection for values beginning with `=`, `+`, `-`, or `@`.
- Never display full PAN/card number, Aadhaar, PAN identity number, secrets, or password data.
- Define configurable maximum rows, date range, file size, job timeout, and retention period.
- Sanitize generated filenames; never derive a filesystem path directly from client input.
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
| `GET /api/admin/beneficiaries` | Paginated beneficiary monitoring/search |
| `GET /api/admin/transactions` | Paginated transaction monitoring/search |
| `GET /api/admin/workflows` | Saga/workflow monitoring, including failures and compensation state |
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

Admin and background Report jobs require direct internal REST contracts because background jobs cannot safely retain customer JWTs. The current public list APIs are not reused as internal aggregation shortcuts.

The required Phase 5 operational surface is:

| Owning service | Required internal operational resources |
| --- | --- |
| Auth | `/internal/operations/users/search`, `/internal/operations/users/summary`; retain the existing notification-recipient lookup |
| Customer | `/internal/operations/customers/search`, `/internal/operations/customers/summary` |
| Branch | `/internal/operations/branches/search`, `/internal/operations/branches/summary` |
| Account | `/internal/operations/accounts/search`, `/internal/operations/accounts/summary` |
| Beneficiary | `/internal/operations/beneficiaries/search`, `/internal/operations/beneficiaries/summary` |
| Transaction | `/internal/operations/transactions/search`, `/internal/operations/transactions/summary`, bounded statement export data |
| Banking Workflow | `/internal/operations/workflows/search`, `/internal/operations/workflows/summary` |
| Bill Payment | `/internal/operations/bill-payments/search`, `/internal/operations/bill-payments/summary`, bounded biller data |
| Card | `/internal/operations/cards/search`, `/internal/operations/cards/summary` |
| Loan | `/internal/operations/loans/search`, `/internal/operations/loans/summary` |
| Banking Scheduler | `/internal/operations/schedules/search`, `/internal/operations/schedules/summary`, bounded execution search |
| Audit | `/internal/operations/audit/search`, `/internal/operations/audit/summary` for Admin aggregation and background audit reports |

Exact request/response DTOs must be documented before their consumers are implemented. Prefer one paginated search contract and one bounded summary contract per resource instead of adding many overlapping endpoints.

Rules:

- Require `X-Internal-Api-Key`.
- Do not route through Gateway.
- Accept explicit requester/owner scope where needed; customer-owned report jobs pass the immutable requester user ID stored when authorization occurred.
- Return DTOs only, with pagination and whitelisted filters/sorts.
- Cap page size, export rows, date range, and multi-value filters at the owning service.
- Do not expose sensitive encrypted fields or repositories.
- Do not mutate domain data.
- Do not accept raw SQL fragments, arbitrary property names, filesystem paths, or unbounded searches.
- Loan search/summary includes `loanType`; Scheduler search distinguishes user schedules from system maintenance schedules.
- Cross-service joins are performed only in Admin/Report memory over bounded DTO results. No SQL join crosses service ownership.

## 9. Kafka and Notification

Audit Service consumes covered banking/audit topics independently of Notification Service.

Report Service publishes:

- `report-requested`
- `report-generated`
- `report-failed`
- `report-downloaded`
- `report-expired`

Notification Service consumes only `report-generated` and `report-failed`. Audit Service consumes the full report lifecycle catalog.

Admin Service publishes sanitized `admin-sensitive-action` events for global searches and other explicitly classified sensitive operations. It does not publish one event for every dashboard refresh or health poll.

Extend the existing Notification Service with a separate report-event listener/template path. Do not combine report file access with SMTP attachment delivery in Phase 5. Notification events contain recipient, report reference, report type, and readiness/failure message, but no filesystem path or financial report contents.

Kafka topic names remain application-level configuration. They need not be placed in `.env`; broker addresses, retry/timeout controls, and credentials remain environment configuration.

## 10. Security and privacy

- JWT and `ADMIN` are mandatory for Audit and Admin APIs.
- Report authorization follows the route matrix; customers can generate/download only their own reports.
- Every protected Phase 5 route enters through Gateway, which validates the JWT `sid` with Auth before forwarding it.
- Phase 5 services independently validate JWT signature, expiry, immutable `sub`, and roles using the shared JWT secret; they do not query `USER_SESSION` directly.
- Logout, logout-all, expiry, deactivation, and role mismatch therefore block subsequent Phase 5 requests with `401`; Auth validation unavailability at Gateway returns `503`.
- Internal operational routes require the internal API key and are absent from Gateway.
- Customer identity comes from JWT at report request time, never from a customer-controlled user-ID field.
- Store requester ID and authorization decision, not the JWT.
- Background workers use the stored requester scope plus internal API key and cannot elevate a customer report to an administrative report.
- Audit and report metadata must be sanitized and size bounded.
- Never log or report passwords, JWTs, API keys, OTPs, Aadhaar, PAN identity values, full card numbers, card encryption material, or unrestricted email bodies.
- Use immutable user IDs instead of usernames for joins and filters.

### Configuration source of truth

- Root `.env` is the single runtime source for Phase 5 database credentials, service URLs, JWT/internal secrets, storage location, limits, timeouts, retention, concurrency, and connection-pool values.
- Compose passes those values into each service. Service `application.yml` files reference required placeholders without fallback defaults.
- Java code and tests must not contain production-like fallback secrets. Tests inject explicit test values through test properties.
- Topic names may remain in service YAML because they are contract names rather than deployment secrets.
- `.env` and generated report content remain Git-ignored; documentation lists variable names without real credentials.

Minimum new runtime variable groups:

| Concern | Variables |
| --- | --- |
| Audit database | `AUDIT_DB_URL`, `AUDIT_DB_USERNAME`, `AUDIT_DB_PASSWORD` |
| Report database | `REPORT_DB_URL`, `REPORT_DB_USERNAME`, `REPORT_DB_PASSWORD` |
| Gateway/service discovery | `AUDIT_SERVICE_URL`, `REPORT_SERVICE_URL`, `ADMIN_SERVICE_URL` plus the domain-service URLs required by Admin/Report |
| Audit consumer | retry count, retry backoff, dead-letter handling limits, metadata-size limit |
| Report generation | storage directory, worker concurrency, scan delay, job timeout, maximum rows/date range/file size, retention |
| Admin aggregation | connect timeout, read timeout, bounded concurrency, summary-cache TTL |

Final variable names must follow the repository's existing uppercase naming convention and be documented in the environment section of the root API documentation. Do not add defaults to service YAML merely to make missing configuration start silently.

## 11. Response and status rules

Use the platform success/error envelopes for JSON APIs.

| Situation | Status |
| --- | ---: |
| Successful read/search | `200` |
| Report accepted | `202` |
| Binary report download | `200` |
| Invalid filter, range, format, or pagination | `400` |
| Missing/invalid/expired JWT or invalidated `sid` | `401` |
| Missing/invalid internal API key | `401` |
| Valid identity with wrong role or ownership | `403` |
| Audit/report resource not found | `404` |
| Duplicate incompatible report idempotency request | `409` |
| Report expired | `410` |
| Gateway cannot validate the session | `503` |
| Required synchronous downstream unavailable | `503` |

Use bounded pagination everywhere. Never return an error status with success data.

A report job whose worker later encounters a downstream failure transitions to `FAILED`; the original queue request remains `202`. Reading a failed job returns `200` with sanitized failure state, not a delayed HTTP `500`.

## 12. Database constraints and indexes

Every owned table has a primary key using the platform's UUID-string convention.

Phase 5 tables live in the existing Oracle schema during local development but remain logically owned: Audit repositories map only Audit tables, Report repositories map only Report tables, and Admin owns no tables.

Audit indexes should cover:

- `(OCCURRED_AT)`
- `(ACTOR_USER_ID, OCCURRED_AT)`
- `(EVENT_TYPE, OCCURRED_AT)`
- `(SOURCE_SERVICE, OCCURRED_AT)`
- `(REFERENCE_ID)`
- `(CORRELATION_ID)`
- `(STATUS, SEVERITY, OCCURRED_AT)` where supported by query design

Report indexes should cover requester/date, status/date, and expiry. Enforce unique job idempotency where supplied and a one-to-one generated-report/job relationship.

`AUDIT_CONSUMER_FAILURES` must uniquely identify the failed Kafka record/attempt state without storing unrestricted raw payloads. `GENERATED_REPORTS.REPORT_JOB_ID` is both a local foreign key and unique.

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
- Existing notification payloads remain consumable after additive audit enrichment.
- A failed email-recipient lookup does not suppress an otherwise auditable domain event.
- `recipient`, email addresses, notification bodies, secrets, and sensitive financial identifiers are not persisted in Audit metadata.
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
- Bill-payment, loan-type, and scheduled-payment/execution reports use real bounded operational endpoints.
- Idempotent replay returns the original job and incompatible key reuse returns `409`.
- Container restart preserves files through the configured volume.

### Admin

- ADMIN-only access.
- Dashboard totals and recent sections match source APIs.
- Bounded parallel fan-out and timeout behavior.
- Partial response marks any temporarily unavailable runtime source explicitly.
- Global search grouping, pagination limits, masking, and sort validation.
- Workflow/Saga monitoring exposes failure and compensation state without permitting mutation.
- System endpoint reports application health only.

### Phase 4 dependency and Phase 1–5 integration

- Phase 5 Loan/Scheduler clients match the completed Phase 4 contracts.
- Loan monitoring/reporting includes the final loan type and Loan-created Audit metadata.
- Scheduler monitoring/reporting distinguishes customer schedules, execution attempts, and system maintenance schedules.
- Kafka Audit adapters accept the final Phase 4 event versions.
- Notification listeners for both phases coexist without duplicate emails.
- Every Phase 5 public route rejects a logged-out, expired, unknown, or role-stale `sid`; Gateway returns `503` when Auth validation is unavailable.
- Every Phase 5 internal operational endpoint rejects missing/invalid internal credentials and is unreachable through Gateway.
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
- Bill-payment, Workflow Saga, and scheduled-execution operational views work through owning-service APIs.
- No Phase 5 service reads another service's tables or mutates banking data.
- Internal operational endpoints are unavailable through Gateway.
- JWT/RBAC and internal-key rules are enforced with correct status codes.
- Gateway `sid` validation applies to all Phase 5 public routes without creating a second session store.
- Phase 5 shared-file changes preserve all Phase 4 routes, services, topics, templates, and documentation.
- Swagger and root documentation describe public/internal APIs, event versions, ownership, download behavior, and degraded responses.
- Clean-schema, Kafka, container restart, failure, security, and complete regression tests pass.

## 16. Implementation constraints for Codex

- Create the Phase 5 branch from baseline `main` commit `70b9cb7` or its approved descendant and record the actual starting SHA.
- Build Audit, Report, and Admin as independently compilable modules first.
- Keep all external-service DTOs local to their consuming Phase 5 module.
- Verify Phase 4 contracts first; add only the missing read-only operational endpoints or audit fields required by the approved checklist.
- Reuse the existing generic `Containerfile`; do not introduce unrelated container abstractions.
- Keep `.env` as the runtime configuration source of truth while leaving topic contract names in YAML.
- Implement one bounded capability at a time, test it, and use short descriptive commits without task numbers.
- Do not commit `.env`, generated reports, report volumes, target folders, or secrets.
- Do not push until explicitly instructed.
- Run the complete Phase 1–5 clean-schema and regression suite before pushing the Phase 5 branch.

## 17. Delivery sequence

Implement Phase 5 in this order:

1. Create the Phase 5 branch from the recorded `main` baseline.
2. Remove the repeated identical Scheduler YAML keys, compile Scheduler, and verify its container without changing configuration behavior.
3. Scaffold `audit-service`, `report-service`, and `admin-service` with conventional controller/service/repository separation where applicable; compile all three.
4. Document the producer/topic/event adapter matrix and implement the bounded existing-producer enrichments.
5. Add and test the owning-service `/internal/operations/**` contracts one service at a time.
6. Implement Audit ingestion, normalization, deduplication, dead-letter behavior, and APIs; test against real Kafka events.
7. Implement asynchronous Report jobs, PDF/CSV generation, storage, download, and report lifecycle events.
8. Extend Notification Service for report completion/failure without disturbing existing listeners.
9. Implement Admin aggregation, dashboard, monitoring/search, degraded responses, and health aggregation.
10. Add Maven modules, Compose services/volume, Gateway public routes, environment variables, Swagger, and root documentation using the existing container pattern.
11. Run clean-schema integration, Kafka failure/restart tests, session/RBAC tests, report-volume restart tests, and the complete Phase 1–5 regression suite.

Each bounded capability is compiled and tested before its short descriptive commit. Do not push until explicitly instructed.
