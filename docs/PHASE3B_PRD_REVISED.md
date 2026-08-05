# Phase 3B PRD

# Internet Banking System

## Bill Payment and Card Management

Status: **Revised for architecture review. Development must not begin until this document is approved.**

This revision aligns Phase 3B with the current platform architecture described in:

- `API_DOCUMENTATION.md`
- `DATA_OWNERSHIP.md`
- `INTERNAL_API_GUIDE.md`
- `ORCHESTRATOR_GUIDE.md`

---

## 1. Important architectural constraints

Phase 3B extends the existing microservice architecture. It must not undo the ownership, security, idempotency, or Saga patterns established in earlier phases.

The following rules are mandatory:

1. The API Gateway is the only public entry point.
2. Browser, mobile, and Postman clients call only `/api/**` routes through the Gateway.
3. `/internal/**` routes are service-to-service contracts. They are not Gateway routes and require `X-Internal-Api-Key`.
4. Public identity comes from the JWT subject (`userId`). A client must not choose its `customerUserId`.
5. No service may access another service's repository or tables, even though local development currently uses one Oracle schema.
6. The Banking Workflow Service coordinates multi-service mutations.
7. Workflow Service persists Saga state in `BANKING_WORKFLOWS`. It is not stateless.
8. All mutating workflows use an `Idempotency-Key` and compare the stored request snapshot before returning a replayed response.
9. Remote mutations must be idempotent using stable workflow-generated references.
10. A failed multi-service workflow must run compensating actions in reverse order.
11. Kafka is used for asynchronous notification events, not for synchronous validation or money movement.
12. Notification Service remains the sole SMTP owner.
13. Existing services may receive small, necessary internal-contract extensions, but must not be redesigned.
14. Real secrets remain only in the ignored `.env` file.

---

## 2. Phase goal

Implement customer-facing bill payment and card-management capabilities while preserving the existing service boundaries.

At the end of Phase 3B, a customer can:

- Browse the active biller catalog.
- Register a biller account against their profile.
- Update or remove their registered biller.
- Pay a registered biller from an owned active bank account.
- View bill-payment history and details.
- View cards issued against their accounts.
- Activate an inactive card.
- Block or unblock a card.
- Update a card's daily transaction limit.

An administrator can:

- Create and maintain the global biller catalog.
- Issue a card for an eligible customer account.
- View customer cards when operational support requires it.
- Block a card administratively.

Successful and relevant failed operations publish Kafka events consumed by Notification Service for email delivery.

---

## 3. Services in scope

### Create

- `billpayment-service`, internal port `8090`
- `card-service`, internal port `8091`

### Extend

- `banking-workflow-service`
- `api-gateway` routing and Compose configuration
- Root Maven module list and developer documentation

### Minimal integration changes permitted

- `notification-service`: only add missing Phase 3B Kafka topic subscriptions or template mapping needed to consume the approved events. Do not redesign its delivery, persistence, retries, SMTP configuration, or public API.
- Existing service internal APIs may be reused. A new internal route may be added only when the owning service lacks a required capability.

### Explicitly not redesigned

- Auth registration and login
- 2FA lifecycle
- Customer profile and KYC
- Account opening
- Beneficiary management
- Existing money workflows
- Notification persistence and SMTP delivery

---

## 4. Architecture

### Simple reads and single-service state changes

```text
Client
  -> API Gateway
  -> Bill Payment Service or Card Service
```

Examples:

- List active billers.
- Register a customer's biller account.
- View bill-payment history.
- View cards.
- Block a card.
- Update a card limit.

### Bill-payment mutation

```text
Client
  -> API Gateway
  -> Banking Workflow Service
       -> Account Service
       -> Bill Payment Service
       -> Transaction Service
       -> Kafka
  -> Notification Service
  -> SMTP
```

The downstream direction is always:

```text
Workflow -> Account
Workflow -> Bill Payment
Workflow -> Transaction
```

Account Service must not call Bill Payment Service or Transaction Service to continue the workflow.

Gateway performs routing and cross-cutting filters only. It must contain no bill, card, balance, or Saga business logic.

---

## 5. Biller domain model

The original PRD mixed the global biller catalog with customer-owned biller registrations. Phase 3B separates them.

### `BILLER_CATALOG`

Owned by Bill Payment Service. Represents an organization that can receive bill payments.

Required fields:

- `billerId`: generated UUID string, primary key
- `billerCode`: generated or administrator-supplied stable unique code
- `billerName`
- `category`: for example `ELECTRICITY`, `WATER`, `GAS`, `TELECOM`, `INTERNET`, `INSURANCE`, `OTHER`
- `status`: `ACTIVE` or `INACTIVE`
- `createdAt`
- `updatedAt`

Required constraints/indexes:

- Primary key on `billerId`
- Unique constraint on `billerCode`
- Index on `(status, category)`
- Index on normalized/searchable biller name if supported by the chosen Oracle mapping

### `CUSTOMER_BILLER`

Owned by Bill Payment Service. Represents a biller account registered by one application user.

Required fields:

- `customerBillerId`: generated UUID string, primary key
- `customerUserId`: JWT subject; cross-service reference with no database foreign key to Auth
- `billerId`: local foreign key to `BILLER_CATALOG`
- `consumerReference`: customer/account/reference number used by the biller
- `nickname`
- `status`: `ACTIVE` or `INACTIVE`
- `createdAt`
- `updatedAt`

Required constraints/indexes:

- Primary key on `customerBillerId`
- Foreign key from `billerId` to local `BILLER_CATALOG`
- Unique constraint on `(customerUserId, billerId, consumerReference)`
- Index on `(customerUserId, status)`

The public response may show the consumer reference, but logs must mask it.

---

## 6. Biller APIs

All public endpoints require JWT unless explicitly marked ADMIN.

### Customer catalog and registration routes

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/billers/catalog` | JWT | List active catalog billers, optionally filtered by category/name |
| `GET /api/billers/catalog/{billerId}` | JWT | Get an active biller's details |
| `GET /api/billers` | JWT | List the authenticated customer's registered billers |
| `POST /api/billers` | JWT | Register a catalog biller for the authenticated customer; returns `201` |
| `GET /api/billers/{customerBillerId}` | owner | Get one registered biller |
| `PUT /api/billers/{customerBillerId}` | owner | Update nickname or consumer reference |
| `DELETE /api/billers/{customerBillerId}` | owner | Remove/deactivate registration; returns `204` |

Customer registration request:

```json
{
  "billerId": "catalog-biller-id",
  "consumerReference": "CONSUMER-12345",
  "nickname": "Home electricity"
}
```

`customerUserId` is always taken from the JWT and is never accepted in this body.

### Administrator catalog routes

| Method and route | Access | Purpose |
| --- | --- | --- |
| `POST /api/billers/catalog` | ADMIN | Create a catalog biller; returns `201` |
| `PUT /api/billers/catalog/{billerId}` | ADMIN | Update catalog metadata/status |
| `DELETE /api/billers/catalog/{billerId}` | ADMIN | Deactivate a catalog biller; returns `204` |

Catalog deletion should normally be a soft deactivation so historical payments remain readable.

---

## 7. Bill-payment domain model

### `BILL_PAYMENTS`

Owned by Bill Payment Service.

Required fields:

- `billPaymentId`: generated UUID string, primary key
- `customerUserId`: immutable JWT subject snapshot
- `customerBillerId`: local foreign key to `CUSTOMER_BILLER`
- `billerId`: biller catalog snapshot/reference
- `billerName`: intentional display snapshot for immutable history
- `consumerReference`: intentional snapshot
- `sourceAccountId`: cross-service Account reference, no database foreign key
- `amount`
- `status`: `PENDING`, `SUCCESS`, `FAILED`, or `CANCELLED`
- `workflowReference`: stable unique Saga reference
- `transactionId`: nullable cross-service reference
- `transactionReference`: nullable stable reference
- `description`
- `failureReason`: sanitized and length-limited
- `createdAt`
- `updatedAt`
- `completedAt`

Required constraints/indexes:

- Primary key on `billPaymentId`
- Unique constraint on `workflowReference`
- Foreign key from `customerBillerId` to local `CUSTOMER_BILLER`
- Positive-amount check
- Index on `(customerUserId, createdAt DESC)`
- Index on `(sourceAccountId, createdAt DESC)`
- Index on `(status, updatedAt)`

The bill-payment record must be created as `PENDING` before the account debit. This gives the Saga a durable bill-payment reference and makes downstream retries idempotent.

---

## 8. Bill-payment public APIs

Payment creation is available only through Workflow. Bill Payment Service exposes read routes publicly.

| Method and route | Access | Purpose |
| --- | --- | --- |
| `POST /api/banking/bill-payments` | JWT + `Idempotency-Key` | Execute the complete bill-payment Saga |
| `GET /api/bill-payments` | JWT | Paginated payment history for current customer |
| `GET /api/bill-payments/{billPaymentId}` | owner/ADMIN | Payment details |
| `GET /api/bill-payments/history` | JWT | Filtered history by date, biller, account, amount, and status |

There must be no public `POST /api/bill-payments` endpoint because it would bypass balance, transaction, idempotency, and compensation rules.

Workflow request:

```json
{
  "sourceAccountId": "account-id",
  "customerBillerId": "registered-biller-id",
  "amount": 1250.00,
  "description": "August electricity bill"
}
```

Workflow response:

```json
{
  "referenceNumber": "BIL-...",
  "billPaymentId": "...",
  "transactionId": "...",
  "sourceAccountId": "...",
  "amount": 1250.00,
  "status": "SUCCESS"
}
```

---

## 9. Bill Payment Service internal APIs

These routes are not exposed through Gateway and require `X-Internal-Api-Key`.

| Method and route | Purpose |
| --- | --- |
| `GET /internal/billers/{customerBillerId}/validate?customerUserId=...` | Validate ownership, registration status, and catalog status |
| `POST /internal/bill-payments` | Idempotently create/return a `PENDING` payment using workflow reference |
| `PUT /internal/bill-payments/{billPaymentId}/complete` | Idempotently mark payment `SUCCESS` with transaction references |
| `PUT /internal/bill-payments/{billPaymentId}/fail` | Idempotently mark payment `FAILED` with sanitized reason |
| `PUT /internal/bill-payments/{billPaymentId}/cancel` | Mark a compensated pending payment `CANCELLED` if that distinction is used |

The service must reject ownership or payload mismatches when the same workflow reference is reused.

---

## 10. Bill-payment Saga

### Happy path

```text
1. Gateway validates/routes authenticated request to Workflow.
2. Workflow obtains customerUserId from JWT.
3. Workflow validates Idempotency-Key and persists BILL_PAYMENT Saga request snapshot.
4. Workflow calls Account internal validation.
5. Workflow verifies account ownership, ACTIVE status, and sufficient balance.
6. Workflow calls Bill Payment internal biller validation.
7. Workflow creates/loads the PENDING bill-payment record using workflowReference.
8. Workflow persists the planned account movement reference.
9. Workflow calls Account internal debit.
10. Workflow persists successful debit state.
11. Workflow persists the planned transaction reference.
12. Workflow calls Transaction internal create.
13. Workflow persists the transaction ID.
14. Workflow calls Bill Payment internal complete.
15. Workflow marks the Saga COMPLETED.
16. Workflow publishes `bill-payment-success` after completion.
17. Workflow returns the stored result.
```

The payment is not considered successful merely because the account was debited. `SUCCESS` requires the debit, transaction record, and Bill Payment completion step to succeed.

### Compensation

If a later step fails, compensation runs in reverse order:

```text
1. Reverse Transaction record if it was created.
2. Reverse Account debit if it was applied.
3. Mark Bill Payment FAILED or CANCELLED.
4. Mark Saga COMPENSATED.
5. Publish bill-payment-failed only after a stable failed/compensated outcome.
```

If any compensation step fails:

- Set Saga to `COMPENSATION_PENDING`.
- Return `503 Service Unavailable`.
- Allow the existing scheduled recovery mechanism to retry compensation.
- Do not publish a final success or failure email until the outcome is stable.

### Idempotency

For `(customerUserId, Idempotency-Key, BILL_PAYMENT)`:

- Same key and identical request returns the stored result.
- Same key and changed account, biller, amount, or description returns `409 Conflict`.
- Internal debit, transaction creation, payment creation, completion, and reversal use stable references and are idempotent.

---

## 11. Bill-payment business rules

- Authentication is required.
- The JWT subject owns the registered biller and source account.
- Amount must be greater than zero and use two-decimal currency precision.
- Source account must exist and be `ACTIVE`.
- Available balance must cover the amount.
- Customer biller registration must be active.
- Catalog biller must be active.
- A payment must start as `PENDING`.
- A payment becomes `SUCCESS` only after the debit and transaction record both succeed.
- Historical display fields are snapshots and must not change when the catalog changes.
- No service reads another service's database tables.
- Scheduled payments are out of scope for this phase.

---

## 12. Card domain model

### `CARDS`

Owned by Card Service.

Required fields:

- `cardId`: generated UUID string, primary key
- `customerUserId`: cross-service Auth reference, no database foreign key
- `accountId`: cross-service Account reference, no database foreign key
- `cardNumberEncrypted`: encrypted full PAN
- `cardNumberHash`: HMAC fingerprint for uniqueness
- `lastFourDigits`: safe display value
- `cardType`: `DEBIT` initially; future-compatible enum
- `status`: `INACTIVE`, `ACTIVE`, `BLOCKED`, or `EXPIRED`
- `dailyTransactionLimit`
- `expiryMonth`
- `expiryYear`
- `blockedReason`
- `createdAt`
- `updatedAt`
- `activatedAt`
- `blockedAt`

Required constraints/indexes:

- Primary key on `cardId`
- Unique constraint on `cardNumberHash`
- Positive daily-limit check
- Valid expiry-month check
- Index on `(customerUserId, status)`
- Index on `(accountId, status)`

### Sensitive card-data rules

- Generate card ID and PAN inside Card Service.
- Encrypt the full PAN at rest with a dedicated `CARD_ENCRYPTION_KEY`.
- Responses return only a masked number such as `************1234`.
- Never return or log the full PAN.
- CVV generation, storage, and verification are out of scope. No CVV column should be created.
- PIN creation and verification are out of scope.
- Expiry is generated internally according to a configurable validity period.

---

## 13. Card APIs

All customer routes derive ownership from the JWT subject.

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/cards` | JWT | List own cards; ADMIN may filter by `customerUserId` |
| `GET /api/cards/{cardId}` | owner/ADMIN | Masked card details |
| `GET /api/cards/{cardId}/status` | owner/ADMIN | Card status and safe summary |
| `POST /api/cards/{cardId}/activate` | owner | Move `INACTIVE` to `ACTIVE` |
| `POST /api/cards/{cardId}/block` | owner/ADMIN | Move an eligible card to `BLOCKED` |
| `POST /api/cards/{cardId}/unblock` | owner/ADMIN | Move `BLOCKED` to `ACTIVE` |
| `PUT /api/cards/{cardId}/limit` | owner | Update daily transaction limit |
| `POST /api/cards` | ADMIN | Issue a card for an eligible active account; returns `201` |

Card issuance is included because a clean schema otherwise contains no cards and the customer card APIs cannot be accepted or tested.

Administrative issue request:

```json
{
  "customerUserId": "user-id",
  "accountId": "account-id",
  "cardType": "DEBIT",
  "dailyTransactionLimit": 50000.00
}
```

Before issuance, Card Service calls Account's internal validation route and requires:

- The account exists.
- The account is active.
- `customerUserId` matches the account owner.
- No conflicting active/inactive card exists when the configured per-account card limit is reached.

Limit update request:

```json
{ "dailyTransactionLimit": 25000.00 }
```

Block request:

```json
{ "reason": "Card misplaced" }
```

---

## 14. Card state rules

Allowed transitions:

```text
INACTIVE -> ACTIVE
INACTIVE -> BLOCKED
ACTIVE   -> BLOCKED
BLOCKED  -> ACTIVE
INACTIVE -> EXPIRED
ACTIVE   -> EXPIRED
BLOCKED  -> EXPIRED
```

Disallowed examples:

- `ACTIVE -> ACTIVE` activation
- `BLOCKED -> BLOCKED` blocking
- Any transition out of `EXPIRED`
- Limit update on an `EXPIRED` card

Additional rules:

- Customers can access only their own cards.
- Administrators can view and operationally block customer cards.
- Limits must be positive and no greater than `CARD_MAX_DAILY_LIMIT`.
- Card payment authorization and daily-limit consumption are not implemented in this phase; the limit is managed state for a future card-payment workflow.
- Customer self-service unblock is allowed for this phase. Step-up 2FA for unblock is a future security enhancement unless explicitly added before implementation.

---

## 15. Workflow Service extension

Add `BILL_PAYMENT` to the existing workflow type enum and add only the Saga fields required to persist its request and downstream results.

Workflow Service continues to own:

- `BANKING_WORKFLOWS`
- Idempotency decisions
- Ordered multi-service execution
- Step/reference persistence
- Compensation and scheduled recovery
- Final workflow event publication

Workflow Service must not own:

- Biller catalog data
- Customer biller registrations
- Bill-payment history
- Cards
- Account balances
- Transaction history
- Notification delivery

The design should allow future workflow types to reuse common execution patterns without introducing a general-purpose workflow engine or excessive abstraction.

---

## 16. Kafka notification events

Kafka is used only after a business state transition has been durably stored.

Approved Phase 3B events:

- `bill-payment-success`
- `bill-payment-failed`
- `card-issued`
- `card-activated`
- `card-blocked`
- `card-unblocked`
- `card-limit-updated`

`bill-payment-created` is not a customer success notification because a newly created payment is still `PENDING`.

Every notification event must contain the existing Notification Service contract fields:

```json
{
  "eventType": "bill-payment-success",
  "referenceNumber": "BIL-...",
  "recipient": "customer@example.com",
  "templateName": "GENERIC_NOTIFICATION",
  "variables": {
    "message": "Your bill payment completed successfully."
  }
}
```

The recipient is obtained from Auth's existing internal notification-recipient endpoint. Email must not be copied into Card or Bill Payment tables.

Notification Service currently subscribes to only some of these Phase 3B topics. The implementation may extend its `@KafkaListener` topic list so the acceptance criteria are achievable. This is an integration-only change; SMTP, delivery state, retry behavior, repositories, entities, and public routes remain untouched.

Events must not contain:

- JWTs
- Passwords or OTPs
- Full card PAN
- Aadhaar or PAN identity values
- Internal API key
- Sensitive consumer-reference values unless masked

Kafka publication failure must not roll back a successfully completed banking operation. It must be logged with the workflow/card reference so delivery can be retried or audited later. A transactional outbox is recommended for a later reliability phase but is not required here.

---

## 17. DTO policy

JPA entities must never be returned by controllers.

### Bill Payment Service

- `BillerCatalogRequest`
- `BillerCatalogResponse`
- `CustomerBillerRequest`
- `CustomerBillerResponse`
- `BillPaymentResponse`
- `BillPaymentSummaryResponse`
- `BillPaymentHistoryResponse`
- `InternalBillerValidationResponse`
- `InternalCreateBillPaymentRequest`
- `InternalCompleteBillPaymentRequest`
- `InternalFailBillPaymentRequest`

### Card Service

- `CardIssueRequest`
- `CardResponse`
- `CardSummaryResponse`
- `CardBlockRequest`
- `CardLimitUpdateRequest`
- `CardStatusResponse`
- `InternalAccountValidationResponse`

### Workflow Service

- `BillPaymentWorkflowRequest`
- `BillPaymentWorkflowResponse`
- Internal Bill Payment, Account, and Transaction client DTOs

DTO validation must use Jakarta validation annotations and return the shared API response/error format.

---

## 18. Response and HTTP status rules

Success envelope:

```json
{
  "success": true,
  "message": "",
  "data": {},
  "timestamp": ""
}
```

Failure envelope:

```json
{
  "success": false,
  "message": "",
  "path": "",
  "timestamp": ""
}
```

Expected statuses:

- `200 OK`: successful read/update/action
- `201 Created`: biller registration, catalog creation, card issue, completed bill-payment workflow response
- `204 No Content`: successful delete/deactivation endpoint where no body is returned
- `400 Bad Request`: validation, insufficient balance, invalid state transition, inactive account/biller
- `401 Unauthorized`: missing or invalid JWT
- `403 Forbidden`: wrong owner or insufficient role
- `404 Not Found`: card, biller, payment, or account not found
- `409 Conflict`: duplicate registration, duplicate card rule, or idempotency payload mismatch
- `503 Service Unavailable`: downstream failure or pending compensation
- `500 Internal Server Error`: unexpected defect only

Never return a success body after an error status, a JWT after failed authentication, or a raw Spring error response when the common error envelope applies.

---

## 19. Security

- JWT required for all public Phase 3B routes.
- Public ownership derives from JWT subject.
- RBAC enforced with `CUSTOMER` and `ADMIN` roles.
- Internal endpoints require `X-Internal-Api-Key`.
- Internal routes are not added to Gateway.
- Full card PAN is encrypted at rest and masked in responses.
- `CARD_ENCRYPTION_KEY` is a dedicated Base64 256-bit key in `.env`.
- Do not reuse `JWT_SECRET`, `TWOFA_ENCRYPTION_KEY`, or `KYC_ENCRYPTION_KEY` for card encryption.
- Logs must not contain JWT, OTP, internal key, full account number, full card number, consumer reference, Aadhaar, PAN, or database/SMTP credentials.

---

## 20. Exception handling and logging

Handle and map at minimum:

- Account not found
- Account ownership mismatch
- Biller catalog entry not found/inactive
- Customer biller not found/inactive
- Bill payment not found
- Card not found
- Insufficient balance
- Invalid card state transition
- Invalid or excessive daily limit
- Duplicate registered biller
- Duplicate/conflicting card issuance
- Idempotency payload mismatch
- Downstream service failure
- Compensation pending
- Validation errors
- Malformed JSON
- Unexpected errors

Log useful identifiers and state transitions:

- Workflow reference and type
- Bill payment started/completed/failed
- Compensation started/completed/pending
- Biller catalog and registration changes
- Card issued/activated/blocked/unblocked/limit updated
- Kafka publication success/failure
- Unexpected errors with stack traces in server logs

Never expose stack traces to API clients.

---

## 21. Configuration and containerization

Add the services to:

- Root `pom.xml`
- `compose.yaml`
- `Containerfile` build arguments through the existing generic service pattern
- API Gateway routes
- Root documentation

Required environment groups:

- `BILLPAYMENT_DB_URL`, `BILLPAYMENT_DB_USERNAME`, `BILLPAYMENT_DB_PASSWORD`
- `CARD_DB_URL`, `CARD_DB_USERNAME`, `CARD_DB_PASSWORD`
- `BILLPAYMENT_SERVICE_URL`
- `CARD_SERVICE_URL`
- `CARD_ENCRYPTION_KEY`
- `CARD_MAX_DAILY_LIMIT`
- Existing `JWT_SECRET`, `INTERNAL_API_KEY`, and `KAFKA_BOOTSTRAP_SERVERS`

Do not commit `.env` or secret values.

The current development DDL source of truth remains the service-local JPA entities. Hibernate `ddl-auto: update` may be used locally. A clean disposable schema must be used for final verification because Hibernate update cannot reliably evolve existing enum check constraints.

---

## 22. Swagger and developer documentation

Expose Swagger for:

- Bill Payment Service
- Card Service
- Updated Workflow Service

Document:

- Public versus internal routes
- JWT and internal API-key requirements
- Biller catalog versus customer registration
- Bill-payment Saga and compensation order
- Idempotency replay rules
- Card state transitions
- Masked card response policy
- Kafka topics and payload contract
- Service dependency map
- Manual test sequence

Update `API_DOCUMENTATION.md`, `INTERNAL_API_GUIDE.md`, and `README.md` after implementation.

---

## 23. Testing requirements

### Build and service tests

- Compile every Maven module.
- Run the Maven test reactor.
- Start all Compose services successfully.
- Verify every container remains running.
- Verify Gateway routing for both new services.

### Bill Payment tests

- Admin creates and updates catalog biller.
- Customer registers, updates, lists, and removes a biller.
- Duplicate customer registration returns `409`.
- Cross-customer access returns `403` or ownership-safe `404` according to the established policy.
- Inactive biller cannot be paid.
- Wrong-owner account cannot be used.
- Inactive account cannot be used.
- Zero/negative amount returns `400`.
- Insufficient balance returns `400` without a debit.
- Successful payment debits exactly once.
- Successful payment creates exactly one transaction.
- Successful payment reaches `SUCCESS`.
- Exact idempotent replay returns the same payment/reference without another debit.
- Changed payload with the same key returns `409`.
- Forced Transaction failure reverses the account debit and produces a stable failed/compensated result.
- Forced compensation failure enters `COMPENSATION_PENDING` and is recovered by the scheduler.
- History, detail, pagination, and filters return only authorized data.

### Card tests

- Admin issues a card only for a matching active account owner.
- Generated PAN is unique, encrypted at rest, and never returned in full.
- Customer sees only their own masked cards.
- Cross-customer access is rejected.
- Valid state transitions succeed.
- Invalid/repeated state transitions return `400` or `409` consistently.
- Positive in-range limit succeeds.
- Zero, negative, or excessive limit returns `400`.
- Expired card cannot be reactivated or unblocked.
- Card events contain no full PAN.

### Kafka and notification tests

- Each approved Phase 3B event reaches Kafka.
- Notification Service consumes each subscribed event.
- Email notification history stores the correct source event and reference.
- Existing Phase 3A registration, login, and transaction notifications still work.
- No duplicate email is emitted for an idempotent workflow replay.

### Schema audit

- Every live Phase 3B table has a primary key.
- Local ownership relationships have foreign keys.
- Cross-service identifiers do not have database foreign keys.
- Required unique constraints and indexes exist.
- No plaintext card PAN is stored.
- All IDs follow the project's current UUID-string convention (`VARCHAR2(36)`).

---

## 24. Deliverables

- Working `billpayment-service`
- Working `card-service`
- Updated Banking Workflow Service with `BILL_PAYMENT` Saga
- Biller catalog and customer biller registration
- Bill-payment history and filters
- Card issuance for testable lifecycle
- Card activation, block, unblock, and limit management
- Bill-payment compensation and recovery
- Kafka notification events
- Minimal Notification topic integration where required
- Gateway and Compose integration
- Entity-owned DDL with constraints and indexes
- Swagger/OpenAPI documentation
- Updated root developer documentation
- Automated tests and final end-to-end report

---

## 25. Out of scope

Do not implement:

- Loan Service
- Audit Service
- AI Service
- Reports or analytics
- Fraud detection
- Card purchase authorization or merchant acquiring
- PIN management
- CVV storage or validation
- Physical card production or delivery tracking
- Scheduled/recurring bill payment
- Partial bill payment
- Bill-fetch integration with external providers
- SMS or push notification
- Redis
- Service discovery
- Distributed tracing
- Full transactional outbox infrastructure

These belong to later phases unless separately approved.

---

## 26. Acceptance criteria

Phase 3B is complete only when:

- Bill Payment Service and Card Service are independently runnable and containerized.
- Gateway routes all public Phase 3B calls.
- Internal APIs are unreachable through Gateway and reject an invalid internal key.
- The biller catalog and customer registrations are distinct and correctly authorized.
- Banking Workflow Service persists and orchestrates the bill-payment Saga.
- A successful bill payment debits the account exactly once, records one transaction, and completes one payment record.
- Idempotent replay never duplicates money movement, transaction, payment, or notification.
- A changed payload with the same idempotency key returns `409`.
- A forced downstream failure proves compensation restores the balance and reverses any transaction record.
- Card issue, view, activation, blocking, unblocking, expiry restrictions, and limit updates work.
- Full card PAN is encrypted at rest and masked everywhere outside Card Service internals.
- Kafka events are published and consumed without breaking existing Phase 3A events.
- Email is delivered only through Notification Service.
- No service directly reads another service's database.
- Correct status codes and common response envelopes are returned.
- Swagger works for both new services and the extended Workflow Service.
- Maven tests pass, all containers remain running, and the clean-schema integration suite passes.

---

## 27. Implementation approval gate

This revised PRD is the proposed source of truth for Phase 3B.

Before development begins, explicitly verify and approve these decisions:

1. Global biller catalog and customer biller registrations are separate entities.
2. Bill payment creation is exposed only as `POST /api/banking/bill-payments` through Workflow.
3. A `PENDING` bill-payment record is created before debit for Saga durability.
4. Workflow continues to persist Saga state and compensation references.
5. Card issuance is an ADMIN endpoint so the card lifecycle can be created and tested from a clean schema.
6. Full card PAN is encrypted; CVV and PIN are out of scope.
7. Minimal Notification Service topic-subscription changes are allowed because several required Phase 3B topics are not currently consumed.
8. Scheduled bill payment and card purchase authorization remain out of scope.

No Phase 3B production code should be written until these decisions are approved.
