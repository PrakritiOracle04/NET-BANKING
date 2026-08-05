# Phase 4 PRD — Loan Management and Banking Scheduler

## 1. Purpose

Phase 4 adds loan servicing and time-based execution without redesigning the existing platform.

Create:

- `loan-service` on port `8092`
- `banking-scheduler-service` on port `8093`

Extend:

- `banking-workflow-service`
- `api-gateway`
- `notification-service` event mappings and templates
- root Maven, Compose, environment documentation, and API documentation

The Banking Workflow Service remains the only service that coordinates multi-service banking mutations. The Banking Scheduler Service is the only component that decides when a scheduled banking operation is due.

## 2. Architectural rules

- Every external request enters through API Gateway.
- Public customer requests use JWT identity and role claims.
- Scheduler-to-Workflow execution is an internal service call over Compose networking and requires `X-Internal-Api-Key`.
- Internal Scheduler and Workflow routes are never exposed by API Gateway.
- Scheduler never debits, credits, records transactions, modifies loans, or creates bill-payment records.
- Workflow calls downstream `/internal/**` APIs and owns Saga ordering, idempotency, and compensation.
- Each service owns its entities, repositories, tables, and business rules.
- No service accesses another service's database tables.
- Cross-service identifiers are application references, not cross-service database foreign keys.
- Kafka is used for asynchronous domain notifications, not synchronous business coordination.

```text
Customer -> Gateway -> Loan/Scheduler read and management APIs
Customer -> Gateway -> Workflow repayment API -> Loan/Account/Transaction
Scheduler -> Workflow internal execution API -> Bill Payment/Account/Transaction
Scheduler -> Workflow internal maintenance API -> Loan overdue/reminder evaluation
Workflow/Scheduler/Loan -> Kafka -> existing Notification Service -> SMTP
```

## 3. Scope

### Included

- Customer and administrator loan views
- Registration of already-approved/disbursed loans by an administrator
- Outstanding balance and EMI schedule
- Loan repayment through Workflow
- Durable loan-repayment history
- EMI calculator without persistence
- EMI due-date reminders
- Automatic overdue EMI and loan-status evaluation
- One-time, daily, weekly, and monthly bill-payment schedules
- Pause, resume, update, cancel, execution history, retries, and recovery
- Kafka email notifications for Phase 4 outcomes

### Not included

- Loan application, underwriting, approval, or fund disbursement
- Automatic EMI collection
- General recurring transfers or standing instructions
- New interest accrual, penalties, restructuring, foreclosure fees, or collections
- Distributed scheduler clustering or Quartz clustering
- Audit, fraud, AI, reporting, dashboards, monitoring, tracing, or service discovery

Administrator loan creation in this phase registers a loan that has already been approved and disbursed outside this system. It must not credit an account. A future origination workflow must handle approval and disbursement atomically.

## 4. Service ownership

### Loan Service

Owns:

- `LOANS`
- `EMI_SCHEDULES`
- `LOAN_REPAYMENTS`

Responsibilities:

- Loan registration by administrators
- Loan ownership and status validation
- Outstanding balance
- EMI schedule generation and allocation
- Idempotent application and reversal of repayments
- Idempotent EMI reminder and overdue evaluation
- Loan history
- Pure EMI calculations

Loan Service never debits an account and never inserts a bank transaction.

### Banking Scheduler Service

Owns:

- `BANKING_SCHEDULES`
- `SCHEDULE_EXECUTIONS`

Responsibilities:

- Schedule lifecycle
- Due-occurrence calculation
- Atomic execution claiming
- Stable execution idempotency keys
- Retry timing and execution history
- Triggering Workflow

Scheduler owns scheduling metadata. It does not own account, loan, biller, bill-payment, or transaction data.

### Banking Workflow Service

Continues to own:

- `BANKING_WORKFLOWS`

Extend it with:

- `LOAN_REPAYMENT` workflow type
- Scheduled bill-payment execution metadata
- Loan repayment Saga steps and compensation
- Trusted internal scheduled-bill-payment endpoint

Scheduled bill payment must reuse the existing bill-payment orchestration logic rather than duplicate it.

## 5. Loan domain

### Loan statuses

- `ACTIVE`
- `CLOSED`
- `OVERDUE`
- `DEFAULTED`

Newly registered loans are `ACTIVE`. A loan becomes `CLOSED` automatically when outstanding principal reaches zero. Administrators may move a loan between `ACTIVE`, `OVERDUE`, and `DEFAULTED` according to valid transition rules, but cannot reopen a `CLOSED` loan in Phase 4.

### EMI statuses

- `PENDING`
- `PARTIALLY_PAID`
- `PAID`
- `OVERDUE`

### Repayment statuses

- `PENDING`
- `SUCCESS`
- `CANCELLED`
- `FAILED`
- `REVERSED`

### Minimum loan fields

- `loanId` — generated UUID string
- `customerUserId`
- `linkedAccountId`
- `loanNumber` — generated unique customer-facing number
- `principalAmount`
- `annualInterestRate`
- `tenureMonths`
- `emiAmount`
- `outstandingBalance`
- `startDate`
- `maturityDate`
- `status`
- `createdAt`, `updatedAt`, `closedAt`

### EMI schedule fields

- `emiScheduleId` — generated UUID string
- `loanId` — local foreign key
- `installmentNumber`
- `dueDate`
- `openingBalance`
- `principalDue`
- `interestDue`
- `totalDue`
- `amountPaid`
- `status`
- `paidAt`
- `reminderSentAt`
- `overdueNotifiedAt`

The pair `(loanId, installmentNumber)` must be unique.

### Loan repayment fields

- `loanRepaymentId` — generated UUID string
- `loanId` — local foreign key
- `customerUserId`
- `sourceAccountId`
- `amount`
- `workflowReference` — unique
- `transactionId`
- `transactionReference`
- `status`
- `failureReason`
- `createdAt`, `updatedAt`, `completedAt`, `reversedAt`

Loan history is sourced from `LOAN_REPAYMENTS`; Loan Service must not read Transaction Service tables.

### Repayment allocation

- Repayment amount must be positive and cannot exceed the current outstanding balance.
- Apply repayment to the oldest unpaid EMI first.
- Within an EMI, apply interest due before principal due.
- Partial payments mark the installment `PARTIALLY_PAID`.
- Fully paid installments become `PAID`.
- Outstanding balance must never become negative.
- Applying or reversing a repayment by `workflowReference` must be idempotent.

## 6. Loan APIs

All public APIs are routed through Gateway.

| Method and path | Access | Behavior |
| --- | --- | --- |
| `POST /api/loans` | ADMIN | Register an already-approved/disbursed loan and generate its EMI schedule; `201` |
| `GET /api/loans` | CUSTOMER/ADMIN | Customer sees own loans; ADMIN may filter by `customerUserId` |
| `GET /api/loans/{id}` | owner/ADMIN | Loan details |
| `GET /api/loans/{id}/balance` | owner/ADMIN | Outstanding balance |
| `GET /api/loans/{id}/schedule` | owner/ADMIN | EMI schedule |
| `GET /api/loans/{id}/history` | owner/ADMIN | Durable repayment history |
| `PUT /api/loans/{id}/status` | ADMIN | Valid administrative status transition |
| `POST /api/loans/calculate` | JWT | Pure EMI calculation; does not persist |

There is no public mutating repayment endpoint in Loan Service.

Customer repayment is executed only through:

```http
POST /api/banking/loans/{loanId}/repay
Authorization: Bearer <JWT>
Idempotency-Key: <unique-key>
Content-Type: application/json

{
  "sourceAccountId": "account-id",
  "amount": 5000.00,
  "description": "August EMI"
}
```

Successful repayment returns `200 OK`. Exact replay returns the original result without a second debit. Reusing the key with a different request returns `409 Conflict`.

### Internal Loan contracts

These require `X-Internal-Api-Key` and are absent from Gateway:

```http
GET  /internal/loans/{id}/validate?customerUserId=...
POST /internal/loan-repayments
PUT  /internal/loan-repayments/{id}/complete
PUT  /internal/loan-repayments/{id}/fail
PUT  /internal/loan-repayments/{id}/reverse
PUT  /internal/loan-repayments/workflow/{reference}/reverse
```

The workflow-reference reversal route covers a lost HTTP response after Loan Service committed the repayment.

## 7. Loan repayment Saga

```text
Client -> Gateway -> Workflow
  1. Validate authenticated user and idempotency request
  2. Validate loan ownership, status, amount, and outstanding balance through Loan Service
  3. Validate source-account ownership, ACTIVE status, and available balance through Account Service
  4. Create PENDING loan repayment using the stable workflow reference
  5. Debit source account using an idempotent movement reference
  6. Record LOAN_REPAYMENT transaction through Transaction Service
  7. Complete Loan repayment and allocate it to EMI rows
  8. Mark Saga COMPLETED
  9. Publish loan-payment-success after commit
```

On failure after any mutation, compensate in reverse order:

```text
reverse/cancel Loan repayment by workflow reference
  -> reverse Transaction record when present
  -> reverse Account debit
  -> mark Saga COMPENSATED
  -> publish loan-payment-failed after compensation stabilizes
```

Every compensation endpoint must be idempotent. Incomplete compensation becomes `COMPENSATION_PENDING` and is retried by the existing Workflow recovery scheduler.

## 8. EMI calculator

Input:

- `loanAmount` greater than zero
- `annualInterestRate` greater than or equal to zero
- `tenureMonths` greater than zero and within a documented upper limit
- optional `startDate`

Output:

- monthly EMI
- total interest
- total repayment
- monthly preview containing installment number, date, opening balance, principal, interest, and closing balance

Use the reducing-balance EMI formula, `BigDecimal`, explicit scale, and `RoundingMode.HALF_UP`. Handle zero interest as `principal / tenure`. Adjust the final installment for accumulated rounding so the closing balance is exactly zero. The calculator performs no database writes and publishes no event.

## 9. Scheduler domain

### Schedule types

- `ONE_TIME`
- `DAILY`
- `WEEKLY`
- `MONTHLY`

Supported operation types are:

- `BILL_PAYMENT` — customer-owned banking schedule
- `EMI_REMINDER_SCAN` — system-owned daily maintenance schedule
- `LOAN_OVERDUE_SCAN` — system-owned daily maintenance schedule

The two loan-maintenance schedules are created from configuration during startup and cannot be created, changed, paused, resumed, or cancelled through customer APIs. New operations may be added through new handlers and Workflow contracts without changing the core schedule/execution schema.

### Schedule statuses

- `ACTIVE`
- `PAUSED`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

### Execution statuses

- `PENDING`
- `RUNNING`
- `RETRY_WAIT`
- `SUCCEEDED`
- `FAILED`

### Minimum schedule fields

- `scheduleId` — generated UUID string
- `customerUserId` — required for customer schedules; null only for protected system schedules
- `operationType`
- `scheduleType`
- `sourceAccountId`
- `customerBillerId`
- `amount`
- `description`
- `timezone`
- `startAt`
- `nextExecutionAt`
- `endAt` — optional
- `maxRetries`
- `systemOwned`
- `status`
- `version` — optimistic locking
- `createdAt`, `updatedAt`

Store execution instants in UTC. Use the schedule timezone to calculate daily, weekly, and monthly occurrences. Monthly schedules retain their requested day; when a month lacks that day, execute on that month's final calendar day.

### Minimum execution fields

- `executionId` — generated UUID string
- `scheduleId` — local foreign key
- `scheduledFor`
- `attemptCount`
- `status`
- `workflowIdempotencyKey`
- `workflowReference`
- `responseSummary`
- `failureReason`
- `startedAt`, `completedAt`, `nextRetryAt`

The pair `(scheduleId, scheduledFor)` and `workflowIdempotencyKey` must be unique.

Bill-payment payload fields are required only for `BILL_PAYMENT`. System maintenance schedules contain no customer, account, biller, or amount. Validation must enforce the field combination for each operation type.

## 10. Scheduler APIs

| Method and path | Access | Behavior |
| --- | --- | --- |
| `POST /api/schedules` | CUSTOMER | Create an owned scheduled bill payment; `201` |
| `GET /api/schedules` | CUSTOMER/ADMIN | Own schedules; ADMIN may filter by customer/status |
| `GET /api/schedules/{id}` | owner/ADMIN | Schedule details |
| `GET /api/schedules/{id}/executions` | owner/ADMIN | Execution history |
| `PUT /api/schedules/{id}` | owner | Update only a non-running, non-terminal schedule |
| `POST /api/schedules/{id}/pause` | owner | `ACTIVE -> PAUSED` |
| `POST /api/schedules/{id}/resume` | owner | `PAUSED -> ACTIVE` and recalculate next occurrence |
| `DELETE /api/schedules/{id}` | owner | Soft-cancel; `204`, never physically delete history |

Schedule creation body:

```json
{
  "scheduleType": "MONTHLY",
  "sourceAccountId": "account-id",
  "customerBillerId": "registered-biller-id",
  "amount": 1250.00,
  "description": "Electricity bill",
  "timezone": "Asia/Kolkata",
  "startAt": "2026-09-01T09:00:00+05:30",
  "endAt": null,
  "maxRetries": 3
}
```

The schedule references a customer biller registration, not an existing bill-payment record. Each execution creates its own bill-payment record through Workflow.

Internal operational endpoint:

```http
POST /internal/schedules/run-due
X-Internal-Api-Key: <INTERNAL_API_KEY>
```

This endpoint is optional for manual/local verification. The service's own `@Scheduled` scanner invokes the same application service directly. It must not call its own HTTP endpoint and the route must not appear in Gateway.

## 11. Scheduler execution and reliability

1. Scan due `ACTIVE` schedules in bounded batches.
2. Atomically claim one occurrence using optimistic locking or a conditional status update.
3. Insert one `SCHEDULE_EXECUTIONS` row for `(scheduleId, scheduledFor)`.
4. Generate a stable key such as `schedule:{scheduleId}:{scheduledFor}`.
5. Call Workflow's internal scheduled-bill-payment endpoint with the internal API key, customer user ID, schedule data, and stable key.
6. On success, mark execution `SUCCEEDED` and calculate the next occurrence from the planned occurrence time.
7. On a retryable `5xx`, timeout, or connection error, mark `RETRY_WAIT` and retry with bounded backoff using the same key.
8. On a terminal `4xx`, mark the occurrence `FAILED` without retrying.
9. A one-time schedule becomes `COMPLETED` after success and `FAILED` after retries are exhausted.
10. A recurring schedule remains `ACTIVE` after an occurrence failure unless its configured end is reached; future occurrences must continue.

The scheduler provides at-least-once delivery to Workflow. Stable idempotency makes the resulting banking operation effectively once-only.

For Phase 4, run one Scheduler replica. The claim and uniqueness rules must still prevent overlapping scanner invocations from executing the same occurrence twice.

### Internal scheduled-payment Workflow contract

```http
POST /internal/workflows/scheduled-bill-payments
X-Internal-Api-Key: <INTERNAL_API_KEY>
Content-Type: application/json

{
  "customerUserId": "user-id",
  "scheduleId": "schedule-id",
  "scheduledFor": "2026-09-01T03:30:00Z",
  "idempotencyKey": "schedule:schedule-id:scheduled-for",
  "sourceAccountId": "account-id",
  "customerBillerId": "registered-biller-id",
  "amount": 1250.00,
  "description": "Electricity bill"
}
```

Workflow must call the same application method used by public `POST /api/banking/bill-payments`. It must not maintain a second copy of the bill-payment Saga.

## 12. EMI reminder and overdue processing

EMI reminders and overdue evaluation are Phase 4 time-based workflows. They must be triggered by Banking Scheduler, not by an independent `@Scheduled` method in Loan Service.

Seed two protected system schedules:

- `EMI_REMINDER_SCAN` — daily in `${BANKING_BUSINESS_TIMEZONE:Asia/Kolkata}`
- `LOAN_OVERDUE_SCAN` — daily after the banking business date changes

The Scheduler sends each occurrence only to Workflow:

```http
POST /internal/workflows/loan-maintenance
X-Internal-Api-Key: <INTERNAL_API_KEY>
Content-Type: application/json

{
  "operationType": "EMI_REMINDER_SCAN",
  "scheduledFor": "2026-09-01T02:30:00Z",
  "businessDate": "2026-09-01",
  "idempotencyKey": "system:emi-reminder:2026-09-01"
}
```

Workflow validates the trusted request and delegates to one of Loan Service's internal endpoints:

```http
POST /internal/loans/maintenance/emi-reminders
POST /internal/loans/maintenance/overdue
```

These routes require the internal API key and are absent from Gateway. Scheduler must never call them directly.

### EMI reminder rules

- Use `${EMI_REMINDER_DAYS_BEFORE:3}` as the default lead time.
- Select `PENDING` or `PARTIALLY_PAID` installments whose due date equals `businessDate + reminderDays`.
- Publish one `emi-reminder` event per eligible installment using a stable event reference such as `emi:{emiScheduleId}:reminder`.
- Record `reminderSentAt` only after Kafka acknowledges publication.
- A retry may republish the same stable reference; Notification Service must deduplicate it.
- A paid installment never receives a reminder.

### Overdue rules

- Select unpaid `PENDING` or `PARTIALLY_PAID` installments with `dueDate < businessDate`.
- Mark each selected installment `OVERDUE` idempotently.
- Mark an `ACTIVE` loan `OVERDUE` when it contains at least one overdue installment.
- Publish one `loan-overdue` event per newly overdue installment using a stable event reference.
- Record `overdueNotifiedAt` only after Kafka acknowledges publication so failed publication can be retried.
- Do not add interest, fees, penalties, or collection actions in Phase 4.
- After repayment, Loan Service recalculates status: `CLOSED` at zero outstanding, `OVERDUE` while any overdue installment remains unpaid, otherwise `ACTIVE`.

Both maintenance operations must be safe to repeat for the same business date. Their Scheduler execution rows provide operational history; EMI status and notification markers provide domain-level idempotency.

## 13. Scheduled bill-payment Saga ordering

Reuse the existing Phase 3B order:

```text
validate account ownership/status/balance
  -> validate registered biller
  -> create durable PENDING bill payment
  -> debit account
  -> create BILL_PAYMENT transaction
  -> complete bill payment
  -> complete Workflow Saga
  -> publish business-success event
```

Existing reverse-transaction, reverse-movement, cancel-payment, lost-response handling, and compensation recovery remain authoritative.

Scheduler records whether Workflow accepted/completed the occurrence, but it does not reproduce Workflow's compensation logic.

## 14. Kafka and notifications

Publish:

- `loan-created` — Loan Service, after administrator registration commits
- `loan-payment-success` — Workflow, after repayment Saga commits
- `loan-payment-failed` — Workflow, after failure is stable or compensation completes
- `emi-reminder` — Loan Service, for an upcoming unpaid installment
- `loan-overdue` — Loan Service, when an unpaid installment first becomes overdue
- `schedule-triggered` — Scheduler, once per claimed occurrence
- `schedule-completed` — Scheduler, once per successful occurrence
- `schedule-failed` — Scheduler, once after an occurrence exhausts retries or fails terminally

Extend the existing Notification Service listener/topic configuration and templates for these events. Do not create another Notification Service or a parallel Kafka consumer architecture. Consumers must tolerate duplicate delivery using the existing event/reference deduplication approach.

Avoid duplicate customer emails: Workflow events describe the banking outcome; Scheduler events describe schedule execution. Notification templates and recipients must make this distinction explicit.

## 15. Security

- JWT is required for all public Loan and Schedule endpoints.
- Customers may access only their own loans, repayments, schedules, and executions.
- Administrators may register loans, update loan status, view customer loans, and inspect schedules.
- Customer identity always comes from the JWT subject, never from a public request body.
- Customer-schedule internal calls carry the immutable `customerUserId` from the owned schedule. System maintenance calls carry no customer identity. Both require the internal API key.
- System-owned maintenance schedules are visible to administrators but cannot be mutated through public APIs.
- Internal routes reject a missing or invalid internal API key with `403` and are not routed by Gateway.
- Never log JWTs, API keys, passwords, OTPs, full account numbers, or sensitive personal/banking data.

## 16. Validation and status codes

Use the platform `ApiResponse` and `ErrorResponse` formats.

| Situation | HTTP status |
| --- | ---: |
| Successful read/update/action | `200` |
| Loan or schedule created | `201` |
| Schedule cancelled | `204` |
| Validation or invalid state transition | `400` |
| Missing/invalid JWT | `401` |
| Wrong role, ownership, or internal key | `403` |
| Owned resource not found | `404` |
| Duplicate loan number, schedule occurrence, or idempotency payload mismatch | `409` |
| Downstream service unavailable | `503` |

Never issue a JWT, mutate a balance, or return success data when returning an error status.

## 17. Database constraints and indexes

Every table must have a primary key. Use UUID strings consistently with existing services.

Required local foreign keys:

- `EMI_SCHEDULES.LOAN_ID -> LOANS.LOAN_ID`
- `LOAN_REPAYMENTS.LOAN_ID -> LOANS.LOAN_ID`
- `SCHEDULE_EXECUTIONS.SCHEDULE_ID -> BANKING_SCHEDULES.SCHEDULE_ID`

Required uniqueness:

- loan number
- `(loanId, installmentNumber)`
- loan repayment workflow reference
- `(scheduleId, scheduledFor)`
- workflow idempotency key

Index ownership/status/date combinations used by list, recovery, and due-scan queries. Do not add database foreign keys across service-owned tables even when local development uses one Oracle schema.

Hibernate entities remain the Phase 4 DDL source of truth using the platform's existing `ddl-auto` approach.

## 18. Logging and documentation

Log identifiers, state transitions, retry attempts, compensation results, event publication, and unexpected failures. Do not log secrets or complete request bodies containing banking identifiers.

Expose Swagger for Loan, Scheduler, and the extended Workflow service. Update root API documentation, internal API documentation, orchestration documentation, README, Compose instructions, environment-variable documentation, service dependency map, Kafka topic map, and manual test examples.

## 19. Testing requirements

### Loan tests

- Admin loan registration and EMI generation
- Customer ownership and admin access
- Calculator formula, zero-interest case, rounding, and final zero balance
- Full, partial, final, excessive, and invalid repayments
- Repayment exact replay and payload mismatch
- Loan auto-close at zero outstanding
- No double application of an internal repayment reference
- Reminder emitted at the configured lead time but never for a paid installment
- Repeated reminder scans reuse the stable reference and do not create duplicate emails
- Unpaid installment and loan transition to `OVERDUE` after the due date
- Repayment returns an overdue loan to `ACTIVE` when no overdue EMI remains
- Failed Kafka publication remains eligible for a later reminder/overdue notification retry

### Repayment Saga tests

- Happy path account debit, transaction, loan allocation, event, and notification
- Failure before mutation
- Failure after account debit
- Failure after transaction creation
- Lost Loan Service response after repayment commit
- Idempotent compensation and scheduled recovery
- Restored account and loan balances after compensation

### Scheduler tests

- One-time, daily, weekly, and monthly next-run calculations
- Timezone and short-month behavior
- Pause, resume, update, and soft cancellation
- Ownership and admin visibility
- Successful scheduled bill payment
- Retryable and terminal failures
- Restart recovery
- Duplicate scanner/concurrent claim prevention
- Stable idempotency across retry and timeout
- Recurring schedule continues after one failed occurrence
- Protected system schedules are seeded exactly once and cannot be changed publicly
- EMI reminder and overdue scans call Workflow, never Loan Service directly
- Repeated/restarted maintenance occurrences do not repeat domain transitions

### Integration regression

- Existing registration, login, 2FA, customer/KYC, account opening, beneficiary, transfer, manual bill payment, cards, Kafka email, and Workflow compensation continue to work.
- All Phase 4 public routes work only through Gateway.
- All internal routes are unavailable through Gateway.
- All services compile and start under Compose against a clean Oracle schema.
- Schema has expected tables, primary keys, local foreign keys, unique constraints, and indexes.

## 20. Acceptance criteria

Phase 4 is complete only when:

- Loan Service and Banking Scheduler Service compile, start, and expose health endpoints.
- Admin can register an already-approved loan and a valid EMI schedule is generated.
- Customers can view only their own loan, balance, EMI schedule, and history.
- Loan repayment is routed through Workflow and is idempotent and compensatable.
- Outstanding balance and EMI allocation remain correct after success, retry, and rollback.
- Customers can create, view, update, pause, resume, and cancel scheduled bill payments.
- Scheduler never calls Account, Transaction, Bill Payment, or Loan Service directly.
- Every due occurrence has one durable execution identity and uses one stable Workflow idempotency key.
- Scheduled bill payments reuse the existing Phase 3B Saga.
- Scheduler triggers daily EMI reminder and overdue workflows through Workflow only.
- Upcoming unpaid EMIs generate one reminder notification at the configured lead time.
- Past-due unpaid EMIs and their loans become overdue without adding penalties or interest.
- Reminder and overdue evaluation remain correct after retries and service restarts.
- Kafka events are consumed by the existing Notification Service and the appropriate email records are created.
- Status codes and response bodies match the documented situations.
- Existing Phase 1–3B integration tests still pass.
- Documentation and Swagger describe all public/internal routes and ownership boundaries.

## 21. Implementation constraints for Codex

- Implement one bounded capability at a time and test it before the next.
- Use short descriptive commits without task numbers.
- Do not redesign or bypass previous services.
- Do not expose internal routes in Gateway.
- Do not duplicate bill-payment orchestration.
- Do not directly edit another service's tables or share JPA entities/repositories.
- Preserve unrelated user changes and never commit `.env` secrets.
- Run a clean-schema integration test, compensation tests, and complete regression before pushing the current Phase 4 branch when explicitly instructed.
