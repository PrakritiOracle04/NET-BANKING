# Internet Banking API and Service Guide

This guide is the starting point for developers integrating with, operating, or extending the platform. External clients must use the API Gateway at `http://localhost:8080`; `/internal/**` routes are service-to-service contracts and are never exposed by the gateway.

See [DATA_OWNERSHIP.md](DATA_OWNERSHIP.md) for the authoritative ownership and duplication rules.
Frontend teams can use [FRONTEND_API_CONTRACT.md](FRONTEND_API_CONTRACT.md) for the compact gateway route contract and tested request bodies.

## Local topology

| Service | Port | Gateway path | Responsibility |
| --- | ---: | --- | --- |
| API Gateway | 8080 | all public paths | CORS, logging, authentication filtering, routing |
| Auth Service | 8081 | `/api/auth/**` | Registration, login, JWT, roles, sessions |
| 2FA Service | 8082 | `/api/2fa/**` | TOTP enrolment, QR generation, verification |
| Customer Service | 8083 | `/api/customers/**` | Customer profile and KYC |
| Branch Service | 8084 | `/api/branches/**` | Branch directory and IFSC validation |
| Account Service | 8085 | `/api/accounts/**` | Account state, balances, statements, movements |
| Beneficiary Service | 8086 | `/api/beneficiaries/**` | Transfer destinations and verification |
| Transaction Service | 8087 | `/api/transactions/**` | Immutable transaction history and statements |
| Banking Workflow Service | 8088 | `/api/banking/**` | Account-opening and money-movement sagas |
| Notification Service | 8089 | `/api/notifications/**` | Kafka consumers, email rendering, SMTP delivery |
| Bill Payment Service | 8090 | `/api/billers/**`, `/api/bill-payments/**` | Biller catalog, registrations, payment history |
| Card Service | 8091 | `/api/cards/**` | Encrypted cards, state transitions, daily limits |
| Loan Service | 8092 | `/api/loans/**` | Loan registration, EMI schedule, balances, repayments |
| Banking Scheduler Service | 8093 | `/api/schedules/**` | Scheduled bill-payment execution and schedule history |

```text
Client -> API Gateway :8080
              |-> Auth :8081
              |-> 2FA :8082
              |-> Customer :8083
              |-> Branch :8084
              |-> Account :8085
              |-> Beneficiary :8086
              |-> Transaction :8087
              |-> Workflow :8088
              `-> Notification :8089
```

## Build and start

1. Create the ignored root `.env` with the database, JWT, encryption, Kafka, and SMTP values.
2. Package all modules: `mvn -DskipTests clean package`.
3. Start the platform: `podman-compose up -d --build`.
4. Inspect a service with `podman-compose logs -f <service-name>`.
5. Stop the project with `podman-compose down`. Oracle is an existing external container and is not removed.

Compose connects services by DNS names such as `customer-service:8083`. The existing host-published Oracle container is reached through `host.containers.internal:1521`. Kafka clients inside Compose use `kafka:29092`; host tools use `localhost:9092`. Kafka UI is at `http://localhost:8081`.

## Authentication and response conventions

`POST /api/auth/register` and `POST /api/auth/login` are public. Other public routes require:

```http
Authorization: Bearer <JWT>
```

The JWT subject is the immutable Auth `userId`. Services authorize ownership with this ID, never with username. Administrative routes also require `ROLE_ADMIN`.

Every login creates a separate session. The JWT contains a `sid` claim that identifies the matching `USER_SESSION` row. Before forwarding a protected request, Gateway asks Auth to verify the JWT signature and expiry, active session, active user, and current role. Tokens issued before `sid` support was introduced require a fresh login.

Successful calls use the common envelope:

```json
{
  "success": true,
  "message": "Operation completed",
  "data": {},
  "timestamp": "2026-08-03T05:00:00Z"
}
```

Errors contain `success:false`, `message`, `path`, and `timestamp`. Expected status families are: `400` malformed request, `401` absent/invalid JWT, `403` insufficient role or ownership, `404` missing resource, `409` state/idempotency conflict, `503` unavailable downstream dependency or pending saga compensation, and `500` unexpected server failure.

Internal routes require this shared header and are not gateway routes:

```http
X-Internal-Api-Key: <INTERNAL_API_KEY>
```

## Customer onboarding lifecycle

Account opening is intentionally a multi-stage process:

```text
Register
  -> complete profile
  -> submit encrypted KYC
  -> administrator verifies KYC
  -> open account through Workflow
  -> Account generates account ID and account number
```

Auth owns verified email and phone. Customer owns personal/address information. Customer KYC owns encrypted Aadhaar and PAN. Account owns account state. Clients do not repeat email/phone during profile completion or supply IDs/account numbers during opening.

### 1. Register

`POST /api/auth/register` returns `201 Created`.

```json
{
  "username": "gokul_test",
  "email": "gokul_test@example.com",
  "phone": "+919876543210",
  "password": "Banking!123",
  "fullName": "Gokul Test"
}
```

Auth creates `APP_USER`, calls Customer's internal create route with only `userId` and `fullName`, then publishes `registration-success` after commit. Username and email are unique. Passwords require uppercase, lowercase, number, special character, and at least eight characters.

### 2. Complete the customer profile

`PUT /api/customers/me`:

```json
{
  "fullName": "Gokul Test",
  "fatherOrSpouseName": "Parent Name",
  "dateOfBirth": "1995-05-15",
  "addressLine1": "10 Example Street",
  "addressLine2": "",
  "city": "Bengaluru",
  "state": "Karnataka",
  "country": "India",
  "postalCode": "560001"
}
```

`GET /api/customers/me` returns the profile. Email and phone are deliberately absent because Auth owns them.

### 3. Submit and review KYC

Customer submission:

```http
PUT /api/customers/me/kyc
```

```json
{
  "aadhaarNumber": "123456789012",
  "panNumber": "ABCDE1234F"
}
```

`GET /api/customers/me/kyc` returns only masked values. Aadhaar and PAN are encrypted with AES-GCM at rest and indexed through non-reversible HMAC fingerprints for uniqueness checks.

Administrative review:

```http
PUT /api/customers/{userId}/kyc/status
```

```json
{ "status": "VERIFIED", "rejectionReason": null }
```

Valid states are `PENDING`, `VERIFIED`, and `REJECTED`. A rejection should include a reason.

### 4. Open an account

`POST /api/banking/accounts/open` returns `201 Created` and requires an `Idempotency-Key`.

```json
{
  "accountType": "SAVINGS",
  "branchIfsc": "ORCL0000001",
  "initialDeposit": 25000.00
}
```

Workflow verifies that the profile is complete, KYC is `VERIFIED`, the IFSC exists, and `initialDeposit` is at least `0.01`. It then calls Account's internal opening route. Account generates a UUID string ID and unique 12-digit account number, stores the initial deposit, uses it for both available and ledger balances, and marks only the customer's first account as primary. Types are `SAVINGS`, `CURRENT`, and `SALARY`.

A replay with the same key and identical body returns the original account. Reusing that key with a different account type, IFSC, or initial deposit returns `409 Conflict`. The opening amount establishes the account's initial balance and is not recorded as a separate deposit transaction.

## Public API reference

All routes below use `http://localhost:8080`.

### Auth

| Method and route | Access | Purpose |
| --- | --- | --- |
| `POST /api/auth/register` | Public | Register app credentials and base customer profile |
| `POST /api/auth/login` | Public | Validate password and optional OTP; return JWT |
| `POST /api/auth/logout` | JWT | Invalidate only the current token's session |
| `POST /api/auth/logout-all` | JWT | Invalidate all active sessions for the current user |
| `GET /api/auth/me` | JWT | Return current Auth identity |

Login accepts `username`, `password`, and optional `otpCode`. When 2FA is enabled, a missing/invalid OTP fails and no JWT is issued. The response's `twoFactorEnabled` reflects the stored factor state.

Session expiry is absolute and is configured once through `JWT_EXPIRATION_MINUTES`; activity does not extend it. Both the JWT and its matching session row use the same calculated expiry. After logout, logout-all, expiry, user deactivation, or a role change, subsequent requests return `401`. Clients must clear the local token and return to login on `401`. Gateway returns `503` if Auth is unavailable because it cannot safely prove the session is active. See [SESSION_MANAGEMENT.md](SESSION_MANAGEMENT.md) for the complete lifecycle.

### 2FA

| Method and route | Purpose |
| --- | --- |
| `POST /api/2fa/setup` | Return secret, `otpauthUri`, and Base64 PNG QR code |
| `POST /api/2fa/verify-setup` | Verify `{ "otpCode": "123456" }` and enable TOTP |
| `POST /api/2fa/verify` | Verify a current code |
| `POST /api/2fa/disable` | Verify a code and disable TOTP |
| `GET /api/2fa/status` | Return current enabled state |

Display the QR as `data:image/png;base64,<qrCodeBase64>`. Do not log or persist the plaintext secret in clients.

### Customer and branch

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/customers/me` | JWT | Own profile |
| `PUT /api/customers/me` | JWT | Complete/update profile |
| `PUT /api/customers/me/kyc` | JWT | Submit or resubmit KYC |
| `GET /api/customers/me/kyc` | JWT | Masked KYC details |
| `GET /api/customers/{customerId}` | ADMIN | Profile lookup |
| `PUT /api/customers/{userId}/kyc/status` | ADMIN | Verify/reject KYC |
| `GET /api/branches` | JWT | List branches |
| `GET /api/branches/ifsc/{ifsc}` | JWT | Resolve IFSC |
| `GET /api/branches/{id}` | JWT | Resolve branch ID |

### Accounts

There is no public account-creation route. Use Workflow account opening.

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/accounts` | JWT | Own accounts; ADMIN may filter `customerUserId` |
| `GET /api/accounts/{id}` | owner/ADMIN | Account details |
| `GET /api/accounts/{id}/balance` | owner/ADMIN | Ledger and available balances |
| `GET /api/accounts/{id}/mini-statement?limit=10` | owner/ADMIN | Recent transactions; limit 1-25 |
| `PUT /api/accounts/{id}/status` | ADMIN | Change account status |

### Beneficiaries

Create and update bodies contain `nickname`, `beneficiaryName`, `relationship`, `accountNumber`, `ifscCode`, and `favourite`. The account must exist and its IFSC must match. `SELF` additionally requires the destination account to belong to the caller. Relationships are `SELF`, `PARENT`, `SPOUSE`, `CHILD`, `SIBLING`, `RELATIVE`, `FRIEND`, `BUSINESS`, and `OTHER`.

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/beneficiaries?favouritesOnly=false` | JWT | List own beneficiaries |
| `POST /api/beneficiaries` | JWT | Create pending beneficiary (`201`) |
| `GET /api/beneficiaries/{id}` | owner/ADMIN | Beneficiary details |
| `PUT /api/beneficiaries/{id}` | owner | Update and reset verification |
| `DELETE /api/beneficiaries/{id}` | owner | Delete (`204`) |
| `PUT /api/beneficiaries/{id}/status` | ADMIN | Set verification state |

### Transactions and workflows

Transaction GET routes expose transaction-by-ID, account history, filtered search, and statements. Transaction creation/reversal is internal because only Workflow may record money movements.

Every banking workflow request requires a client-generated `Idempotency-Key`:

| Method and route | Purpose | Body |
| --- | --- | --- |
| `POST /api/banking/accounts/open` | Validate onboarding and open account | `accountType`, `branchIfsc`, `initialDeposit` |
| `POST /api/banking/deposit` | Credit owned active account and record transaction | `accountId`, `amount`, optional `description` |
| `POST /api/banking/withdraw` | Validate funds, debit, and record transaction | `accountId`, `amount`, optional `description` |
| `POST /api/banking/transfer` | Verify beneficiary, debit source, credit destination, record both sides | `sourceAccountId`, `destinationAccountNumber`, `amount`, optional `description` |

Workflow is the coordinator. Account never calls Beneficiary or Transaction to continue a workflow. The gateway sends the initial request to Workflow, and Workflow calls each required internal API in order.

### Notifications

| Method and route | Purpose |
| --- | --- |
| `POST /api/notifications/email/send` | Render and send a named template |
| `POST /api/notifications/email/test` | Direct generic SMTP test |
| `POST /api/notifications/email/test-kafka` | Publish and consume a Kafka test event |
| `GET /api/notifications/email/{id}` | Delivery details |
| `GET /api/notifications/email/history` | Newest-first delivery history |
| `POST /api/notifications/email/{id}/retry` | Retry a failed notification |
| `GET /api/notifications/email/failed` | Failed deliveries |
| `GET /api/notifications/email/pending` | Pending deliveries |

Normal business notifications are asynchronous: producer -> Kafka -> Notification Service -> template -> SMTP. States are `PENDING`, `PROCESSING`, `SENT`, `FAILED`, and `RETRYING`.

### Billers and bill payments

Bill Payment Service separates the administrator-managed catalog from customer-owned registrations.

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/billers/catalog` | JWT | List active catalog billers; optional `category` filter |
| `POST /api/billers/catalog` | ADMIN | Create catalog biller (`201`) |
| `PUT /api/billers/catalog/{id}` | ADMIN | Update catalog biller |
| `DELETE /api/billers/catalog/{id}` | ADMIN | Soft-deactivate catalog biller (`204`) |
| `GET /api/billers` | JWT | List own registered billers |
| `POST /api/billers` | JWT | Register a catalog biller (`201`) |
| `GET /api/billers/{id}` | owner | Registered biller details |
| `PUT /api/billers/{id}` | owner | Update registration |
| `DELETE /api/billers/{id}` | owner | Deactivate registration (`204`) |
| `GET /api/bill-payments` | JWT | Paginated own payment history |
| `GET /api/bill-payments/history` | JWT | Filter by status/account/biller/date |
| `GET /api/bill-payments/{id}` | owner/ADMIN | Payment details |

Bill payment creation exists only at `POST /api/banking/bill-payments` and requires `Idempotency-Key`:

```json
{
  "sourceAccountId": "account-id",
  "customerBillerId": "registered-biller-id",
  "amount": 1250.00,
  "description": "Electricity bill"
}
```

Workflow creates a durable `PENDING` payment, debits Account, records a `BILL_PAYMENT` transaction, completes the payment, and publishes `bill-payment-success`. A later failure reverses the transaction and debit, cancels the payment, and publishes `bill-payment-failed` only after compensation stabilizes.

### Cards

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/cards/products` | JWT | Debit and credit card product catalog for dropdowns |
| `POST /api/cards/applications` | CUSTOMER | Submit a debit or credit card application for an owned account (`201`) |
| `GET /api/cards/applications` | CUSTOMER | List own card applications |
| `GET /api/cards/applications/{id}` | owner/ADMIN | Card application details |
| `GET /api/cards/admin/applications?status=PENDING&page=0&size=50` | ADMIN | Review card applications |
| `POST /api/cards/admin/applications/{id}/approve` | ADMIN | Approve application and issue an inactive card |
| `POST /api/cards/admin/applications/{id}/reject` | ADMIN | Reject application with reason |
| `GET /api/cards` | JWT | List own cards; ADMIN may filter `customerUserId` |
| `GET /api/cards/{id}` | owner/ADMIN | Masked card details |
| `GET /api/cards/{id}/status` | owner/ADMIN | Safe card status summary |
| `GET /api/cards/credit-accounts` | owner/ADMIN | Credit-card account summaries; ADMIN may filter `customerUserId` |
| `GET /api/cards/{id}/credit-account` | owner/ADMIN | Credit-card account linked to a card |
| `POST /api/cards/{id}/activate` | owner | `INACTIVE -> ACTIVE` |
| `POST /api/cards/{id}/block` | owner/ADMIN | `INACTIVE/ACTIVE -> BLOCKED` |
| `POST /api/cards/{id}/unblock` | owner/ADMIN | `BLOCKED -> ACTIVE` |
| `PUT /api/cards/{id}/limit` | owner | Update positive configured-range daily limit |

Card applications are now the frontend path. Direct `POST /api/cards` is intentionally retired from the public/frontend contract; card issuance happens through admin approval. Products support `DEBIT` and `CREDIT`. Only one pending application or non-expired card is allowed per account per card type, so one debit and one credit card may coexist for the same account.

Card Service generates the 16-digit Luhn-valid PAN internally, encrypts it with AES-256-GCM, stores an HMAC fingerprint for uniqueness, and returns only `************1234`. CVV and PIN are not stored or implemented. Credit-card approval also creates a credit-card account record with configured limit, available credit, outstanding balance, billing-cycle day, and status.

### Loans

| Method and route | Access | Purpose |
| --- | --- | --- |
| `GET /api/loans/types` | JWT | Loan type catalog for frontend dropdowns |
| `POST /api/loans/applications` | CUSTOMER | Submit a loan application for an owned linked account (`201`) |
| `GET /api/loans/applications` | CUSTOMER | List own loan applications |
| `GET /api/loans/applications/{id}` | owner/ADMIN | Loan application details |
| `GET /api/loans/admin/applications?status=PENDING&page=0&size=50` | ADMIN | Review loan applications |
| `POST /api/loans/admin/applications/{id}/approve` | ADMIN | Approve application, issue loan, and generate EMI schedule |
| `POST /api/loans/admin/applications/{id}/reject` | ADMIN | Reject application with reason |
| `POST /api/loans` | ADMIN/internal | Directly register an already approved/disbursed loan; not part of the frontend customer flow |
| `POST /api/loans/calculate` | JWT | Calculate EMI and preview schedule |
| `GET /api/loans` | owner/ADMIN | Own loans; ADMIN may filter `customerUserId` and `status` |
| `GET /api/loans/{id}` | owner/ADMIN | Loan details |
| `GET /api/loans/{id}/balance` | owner/ADMIN | Outstanding balance and EMI |
| `GET /api/loans/{id}/schedule` | owner/ADMIN | EMI schedule |
| `GET /api/loans/{id}/history` | owner/ADMIN | Repayment history |
| `PUT /api/loans/{id}/status` | ADMIN | Move loan status |
| `POST /api/banking/loans/{loanId}/repay` | owner | Repay through Workflow; requires `Idempotency-Key` |

Loan types are `HOME`, `VEHICLE`, `PERSONAL`, `EDUCATION`, and `BUSINESS`. Loan statuses are `ACTIVE`, `OVERDUE`, `DEFAULTED`, and `CLOSED`. Loan repayment is coordinated by Workflow so account debit, transaction recording, loan allocation, and compensation stay consistent.

### Schedules

| Method and route | Access | Purpose |
| --- | --- | --- |
| `POST /api/schedules` | owner | Create scheduled bill payment (`201`) |
| `GET /api/schedules` | owner/ADMIN | Own schedules; ADMIN may filter `customerUserId` and `status` |
| `GET /api/schedules/{id}` | owner/ADMIN | Schedule details |
| `GET /api/schedules/{id}/executions` | owner/ADMIN | Execution attempts and workflow references |
| `PUT /api/schedules/{id}` | owner | Update non-terminal schedule |
| `POST /api/schedules/{id}/pause` | owner | Pause active schedule |
| `POST /api/schedules/{id}/resume` | owner | Resume paused schedule |
| `DELETE /api/schedules/{id}` | owner | Soft-cancel schedule (`204`) |

Customer schedules currently execute `BILL_PAYMENT` operations. Supported schedule types are `ONE_TIME`, `DAILY`, `WEEKLY`, and `MONTHLY`. Execution instants are stored in UTC and recurrence is calculated from the submitted timezone.

## Phase 4 Smoke-Test Notes

The following gateway flows were manually verified on 2026-08-04:

| Area | Result |
| --- | --- |
| Direct bill payment through `POST /api/banking/bill-payments` | Passed |
| One-time scheduled bill payment through `/api/schedules` | Passed |
| Card issue/list/activate/limit/block/unblock | Passed |
| Notification history and manual test email record creation | Passed |

During testing, `BANK_TRANSACTIONS` was recreated from the current Transaction entity so it uses `CUSTOMER_USER_ID` and accepts `BILL_PAYMENT` transaction types. If an older local schema recreates `CUSTOMER_USERNAME`, rebuild and recreate `transaction-service` from a fresh JAR/image before retesting.

## Phase 5 Operations APIs

All Phase 5 public routes are accessed through Gateway `http://localhost:8080`. Gateway validates the JWT `sid` with Auth before routing; the destination service also validates JWT signature, expiry, subject, `sid`, and roles.

### Audit (`ADMIN`)

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/api/audit` | Paginated filtered audit search |
| GET | `/api/audit/{id}` | One immutable audit record |
| GET | `/api/audit/users/{userId}` | User timeline |
| GET | `/api/audit/timeline` | Chronological filtered timeline |
| GET | `/api/audit/summary` | Bounded counts by event type, status, and severity |

Supported filters include `from`, `to`, `actorUserId`, `action`, `sourceService`, `entityType`, `referenceId`, `correlationId`, `status`, and `severity`. Page size and date ranges are capped.

### Reports

Every creation route accepts optional `Idempotency-Key` and returns `202 Accepted`. The request body is:

```json
{
  "format": "CSV",
  "ownerUserId": null,
  "accountId": "required-for-account-statements",
  "from": "2026-08-01T00:00:00Z",
  "to": "2026-08-05T23:59:59Z",
  "filters": {}
}
```

| Method | Route | Access |
| --- | --- | --- |
| POST | `/api/reports/account-statements` | owner/ADMIN |
| POST | `/api/reports/transactions` | CUSTOMER/ADMIN |
| POST | `/api/reports/customers` | ADMIN |
| POST | `/api/reports/cards` | owner/ADMIN |
| POST | `/api/reports/loans` | owner/ADMIN |
| POST | `/api/reports/bill-payments` | owner/ADMIN |
| POST | `/api/reports/schedules` | owner/ADMIN |
| POST | `/api/reports/admin-overview` | ADMIN |
| POST | `/api/reports/audit` | ADMIN |
| GET | `/api/reports/{id}` | requester/ADMIN |
| GET | `/api/reports/history` | requester; ADMIN may filter `requesterUserId` |
| GET | `/api/reports/{id}/download` | requester/ADMIN; binary response |

Report workers use the immutable requester scope stored in `REPORT_JOBS`; they never store or reuse a JWT. Files are generated in the configured report volume and filesystem paths are never returned publicly.

### Admin (`ADMIN`)

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/api/admin/dashboard` | Concurrent operational summaries with section status |
| GET | `/api/admin/customers` | Customer monitoring |
| GET | `/api/admin/accounts` | Account monitoring |
| GET | `/api/admin/beneficiaries` | Beneficiary monitoring |
| GET | `/api/admin/transactions` | Transaction monitoring |
| GET | `/api/admin/workflows` | Saga and compensation monitoring |
| GET | `/api/admin/loans` | Loan monitoring including loan type |
| GET | `/api/admin/cards` | Masked card monitoring |
| GET | `/api/admin/branches` | Branch monitoring |
| GET | `/api/admin/bill-payments` | Bill-payment monitoring |
| GET | `/api/admin/schedules` | Schedule monitoring |
| GET | `/api/admin/audit-summary` | Audit counts |
| GET | `/api/admin/system` | Service health aggregation |
| GET | `/api/admin/search?query=...` | Bounded grouped global search |

Admin is read-only and owns no database tables. Dashboard failures are reported per section; `503` is returned when all core sources are unavailable.

## Internal dependency map

| Caller | Callee | Contract | Reason |
| --- | --- | --- | --- |
| Gateway | Auth | `POST /internal/auth/sessions/validate` | Enforce active, unexpired, non-revoked sessions before routing |
| Auth | Customer | `POST /internal/customers` | Create minimal profile during registration |
| Auth | 2FA | status and verify routes | Enforce TOTP before issuing JWT |
| Workflow | Customer | `GET /internal/customers/{userId}/onboarding-status` | Profile/KYC prerequisite |
| Workflow | Branch | `GET /internal/branches/ifsc/{ifsc}` | Validate opening branch |
| Workflow | Account | `POST /internal/accounts/open` | Idempotent account creation using the requested initial deposit |
| Beneficiary | Account | account-number validation | Validate destination and IFSC |
| Workflow | Account | validation, debit, credit, reversal routes | Apply/reverse idempotent movements |
| Workflow | Beneficiary | `POST /internal/beneficiaries/verify-transfer` | Require verified destination |
| Workflow | Transaction | create and reverse routes | Persist/reverse transaction records |
| Workflow | Bill Payment | validation, pending, complete, cancel routes | Execute and compensate bill-payment Saga |
| Card | Account | account validation route | Verify issue account and owner |
| Card | Auth | notification-recipient route | Obtain current email without duplicating it |
| Account | Transaction | recent-transactions route | Read-only mini statement composition |
| Auth/Workflow | Kafka | domain topics | Publish notification events after success |
| Notification | Kafka | domain topics | Consume and deliver email |

## Saga behavior

`BANKING_WORKFLOWS` stores the workflow type, request identity, status, request snapshot, downstream references, and failure/compensation state. Each mutating step is persisted before the next remote call. Money-movement compensation runs in reverse order:

```text
reverse recorded credit transaction
  -> reverse recorded debit transaction
  -> reverse destination movement
  -> reverse source movement
```

Account opening has no compensating deletion: all prerequisites are checked before creation, and the Account internal API is idempotent by `openingReference`. If Workflow loses the response, retrying safely returns the already-created account.

`COMPENSATION_PENDING` means an automatic scheduled retry is required; clients receive `503`. A completed exact replay returns the stored response. A key reused for different input returns `409`.

## Database source of truth

The service-local JPA entities and their `@Table`, `@Column`, `@Index`, `@UniqueConstraint`, and relationship annotations are the current DDL source of truth. Every data-owning service uses Hibernate `spring.jpa.hibernate.ddl-auto=update` against the shared local Oracle schema.

This is convenient for development but is not a production migration strategy. Hibernate update does not reliably evolve existing enum check constraints or perform destructive renames. After an entity contract changes, use a fresh disposable schema for validation. Before production, introduce ordered Flyway or Liquibase migrations and change Hibernate to `validate`.

Identifiers are UUID values represented as `VARCHAR2(36)` because the project intentionally kept portable string UUID storage. Primary keys, uniqueness rules, foreign keys that remain inside the same service-owned data boundary, and query indexes are defined in the entities. Cross-service references such as `CUSTOMER_USER_ID` are intentionally not database foreign keys because the owning record belongs to Auth.

## Required environment variables

Keep actual values only in the ignored `.env`. It is the required single source for every `${VARIABLE}` referenced by application YAML and Compose. There are no YAML fallback values; a missing variable is a startup/configuration error.

| Variable group | Purpose |
| --- | --- |
| `*_DB_URL`, `*_DB_USERNAME`, `*_DB_PASSWORD` | Oracle connection for each data-owning service |
| `JWT_SECRET` | Shared JWT signature verification secret |
| `JWT_EXPIRATION_MINUTES` | Required single source for both JWT and session lifetime; local `.env` uses `30` |
| `SESSION_CLEANUP_DELAY_MS` | Interval for marking expired session rows; default `60000` |
| `GATEWAY_SESSION_VALIDATION_TIMEOUT_MS` | Gateway-to-Auth validation timeout; default `3000` |
| `INTERNAL_API_KEY` | Shared credential for `/internal/**` calls |
| `TWOFA_ENCRYPTION_KEY` | AES key for TOTP secrets |
| `KYC_ENCRYPTION_KEY` | AES key and fingerprint derivation material for Aadhaar/PAN |
| `CARD_ENCRYPTION_KEY` | Independent AES key and fingerprint derivation material for card PAN |
| `CARD_MAX_DAILY_LIMIT` | Upper bound for customer card limits |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka connection |
| `SMTP_*` | Notification-only SMTP transport and sender settings |
| `*_SERVICE_URL` | Gateway and internal client destinations |
| `CORS_ALLOWED_ORIGINS` | Browser origins allowed by Gateway |

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Registration fails | Auth and Customer logs; shared internal key |
| Login fails after 2FA | Current OTP and 2FA availability |
| Business route returns `401` | Bearer token, JWT `sid`, session status/expiry, user status, and current role |
| Protected route returns `503` | Auth health and Gateway `AUTH_SERVICE_URL`; Gateway fails closed when session validation is unavailable |
| Internal call returns `401/403` | Exact `INTERNAL_API_KEY` match |
| Account opening returns `409` | Profile completeness, KYC status, idempotency reuse |
| Old enum produces Oracle check violation | Recreate disposable schema or add an explicit migration |
| Notification retries/fails | Kafka health, recipient, SMTP app password/TLS |
| Container exits | `podman-compose logs <service-name>` and Oracle reachability |
| Oracle approaches process limit | Compose bounds each Hikari pool with `DB_POOL_MAX_SIZE` (default 4) and `DB_POOL_MIN_IDLE` (default 1) |
