# Phase 5 Event Catalog

This catalog is the implementation contract between domain producers, Notification Service, and Audit Service. Topic names are application contracts and remain in service YAML. Broker addresses and runtime controls come from `.env`.

## Compatibility rules

- Existing topic names and Notification-required fields remain unchanged.
- Additive fields do not require Notification consumers to use them.
- A domain event is published even when notification-recipient lookup fails. In that case `recipient` is absent/null and Notification skips email while Audit still records the event.
- Audit never stores `recipient`, email addresses, message bodies, secrets, JWTs, OTPs, complete account numbers, Aadhaar, PAN, or full card numbers.
- Existing payloads are normalized by topic-specific Audit adapters. New Phase 5 lifecycle events use the canonical envelope from the Phase 5 PRD.

## Existing topics

| Topic | Producer | Audit action/entity | Actor/reference source |
| --- | --- | --- | --- |
| `registration-success` | Auth | `USER_REGISTERED` / `USER` | `actorUserId`, registration reference |
| `login-alert` | Auth | `LOGIN_SUCCEEDED` / `SESSION` | `actorUserId`, login reference |
| `transaction-created` | Workflow | `TRANSACTION_CREATED` / `TRANSACTION` | `actorUserId`, workflow reference |
| `account-debited` | Workflow | `ACCOUNT_DEBITED` / `ACCOUNT` | `actorUserId`, account ID and workflow reference |
| `account-credited` | Workflow | `ACCOUNT_CREDITED` / `ACCOUNT` | `actorUserId`, account ID and workflow reference |
| `bill-payment-success` | Workflow | `BILL_PAYMENT_COMPLETED` / `BILL_PAYMENT` | `actorUserId`, workflow reference |
| `bill-payment-failed` | Workflow | `BILL_PAYMENT_FAILED` / `BILL_PAYMENT` | `actorUserId`, workflow reference |
| `loan-payment-success` | Workflow | `LOAN_REPAYMENT_COMPLETED` / `LOAN_REPAYMENT` | `actorUserId`, workflow reference |
| `loan-payment-failed` | Workflow | `LOAN_REPAYMENT_FAILED` / `LOAN_REPAYMENT` | `actorUserId`, workflow reference |
| `card-issued` | Card | `CARD_ISSUED` / `CARD` | `actorUserId`, card reference |
| `card-activated` | Card | `CARD_ACTIVATED` / `CARD` | `actorUserId`, card reference |
| `card-blocked` | Card | `CARD_BLOCKED` / `CARD` | `actorUserId`, card reference |
| `card-unblocked` | Card | `CARD_UNBLOCKED` / `CARD` | `actorUserId`, card reference |
| `card-limit-updated` | Card | `CARD_LIMIT_UPDATED` / `CARD` | `actorUserId`, card reference |
| `loan-created` | Loan | `LOAN_REGISTERED` / `LOAN` | `customerUserId`, loan ID/number; metadata includes `loanType` |
| `emi-reminder` | Loan | `EMI_REMINDER_EMITTED` / `LOAN` | `customerUserId`, loan ID |
| `loan-overdue` | Loan | `LOAN_OVERDUE` / `LOAN` | `customerUserId`, loan ID |
| `schedule-triggered` | Scheduler | `SCHEDULE_TRIGGERED` / `SCHEDULE_EXECUTION` | `actorUserId`, schedule ID and workflow idempotency key |
| `schedule-completed` | Scheduler | `SCHEDULE_COMPLETED` / `SCHEDULE_EXECUTION` | `actorUserId`, schedule ID and workflow idempotency key |
| `schedule-failed` | Scheduler | `SCHEDULE_FAILED` / `SCHEDULE_EXECUTION` | `actorUserId`, schedule ID and workflow idempotency key |

## Required Phase 5 lifecycle events

These events are additive. Notification subscribes only when an email is required; Audit consumes all of them.

| Topic/event type | Producer | Purpose |
| --- | --- | --- |
| `authentication-failed` | Auth | Sanitized failed-login outcome; no submitted username/email in metadata |
| `session-logout` | Auth | Current `sid` invalidated; do not place the JWT or raw session token in metadata |
| `session-logout-all` | Auth | All active sessions invalidated for a user |
| `account-opened` | Workflow | Completed account-opening Saga |
| `account-status-changed` | Account | Administrative status transition |
| `transaction-reversed` | Transaction | Successful idempotent reversal |
| `workflow-completed` | Workflow | Deposit/withdrawal/transfer and other Saga success with workflow type |
| `workflow-failed` | Workflow | Final Saga failure with bounded reason code |
| `workflow-compensated` | Workflow | Compensation completed or remains pending |
| `beneficiary-created` | Beneficiary | Beneficiary added |
| `beneficiary-updated` | Beneficiary | Beneficiary details changed |
| `beneficiary-deleted` | Beneficiary | Beneficiary removed |
| `beneficiary-status-changed` | Beneficiary | Administrative status transition |
| `kyc-submitted` | Customer | KYC submitted without identity values |
| `kyc-status-changed` | Customer | Administrative KYC outcome |
| `loan-status-changed` | Loan | Administrative/automatic loan status transition |
| `schedule-created` | Scheduler | Customer schedule created |
| `schedule-updated` | Scheduler | Customer schedule updated |
| `schedule-paused` | Scheduler | Customer schedule paused |
| `schedule-resumed` | Scheduler | Customer schedule resumed |
| `schedule-cancelled` | Scheduler | Customer schedule cancelled |
| `report-requested` | Report | Authorized job accepted |
| `report-generated` | Report | File metadata committed and ready |
| `report-failed` | Report | Job reached terminal failure |
| `report-downloaded` | Report | Authorized download completed |
| `report-expired` | Report | Generated content expired/removed |
| `admin-sensitive-action` | Admin | Bounded global search or explicitly sensitive administrative read |

## Audit identity and deduplication

For a canonical event, Audit uses the producer `eventId`. For an existing payload without one, Audit derives a deterministic ID from topic, partition, and offset. The Kafka coordinate tuple is independently unique in `AUDIT_LOGS`. Audit ingestion does not call domain services to reconstruct missing actor identity.
