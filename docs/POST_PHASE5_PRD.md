# Post-Phase-5 PRD — Password Recovery and Card Applications

**Revision:** Final closeout requirements after Phase 5

**Current implementation baseline:** `1a36ad1` (`phase5`)

**Baseline date:** 2026-08-05

**Implementation status:** Proposed; no production code may be written until the approval gate in Section 22 is accepted.

## 1. Purpose

This closeout release adds two missing customer journeys without creating another platform phase or introducing a new microservice:

1. Secure password recovery by email, six-digit OTP verification, and one-time password reset authorization.
2. Customer card applications followed by administrator approval or rejection before card issuance.

Extend only where required:

- `auth-service`
- `notification-service`
- `card-service`
- `account-service` internal validation usage only; no account-domain redesign
- `api-gateway`
- Audit event coverage
- root environment, Compose wiring only when a new variable is required, and developer/frontend API documentation

Do not create an AI service, password-reset service, card-application service, workflow type, or new orchestration service for these requirements.

The completed Phase 5 platform remains the baseline, including:

- API Gateway on port `8080`
- signed JWTs containing immutable user ID in `sub`, roles, and revocable session ID in `sid`
- Gateway session validation against Auth Service for every protected `/api/**` request
- `X-Internal-Api-Key` for service-to-service APIs
- Auth-owned `APP_USER` and `USER_SESSIONS`
- Notification-owned templates, delivery history, Kafka consumers, and SMTP integration
- Card-owned encrypted card PAN and card lifecycle
- Account ownership and activity validation through Account Service
- Kafka-backed audit and notification events
- one Oracle instance/schema in local development with strict logical service ownership

## 2. Baseline and development gate

Implementation must start from the approved post-Phase-5 `main` commit. If `1a36ad1` has not yet been merged, merge Phase 5 first or explicitly approve a branch based on that commit.

Before implementation:

- Full Maven reactor tests must pass.
- All Compose application containers must remain running.
- Registration, login with and without 2FA, logout, logout-all, session expiry, email delivery, card issuance/lifecycle, Audit ingestion, Report generation, and Admin reads must still pass.
- The current `.env` remains the runtime configuration source of truth. Service YAML may reference variables without defaults. Kafka topic names remain application configuration and do not need to move into `.env`.
- The generic `Containerfile` must remain unchanged unless a separately demonstrated build defect is approved.
- The existing `frontend_api_documentation.md` must be updated only after the APIs in this PRD are implemented and verified.

## 3. Architectural rules

- Every frontend request enters through API Gateway.
- Password-reset request, OTP verification, and reset confirmation are public because the user has no active session.
- Public reset responses must not reveal whether an email is registered.
- Auth Service owns password-reset state, OTP verification, reset-token verification, password mutation, and session invalidation.
- Notification Service owns email rendering, SMTP delivery attempts, and delivery history.
- Card Service owns card applications, review state, product eligibility, and issued cards.
- Account Service remains the authority for account existence, ownership, and ACTIVE status.
- Admin Service remains an operational read service and must not approve or reject card applications. The Admin frontend calls Card Service public ADMIN routes through Gateway.
- Banking Workflow Service is not used. Card application approval is a single Card Service transaction plus read-only Account validation; it is not a multi-service money-movement Saga.
- No service reads or writes another service's tables.
- Cross-service identifiers remain scalar references and do not become cross-service JPA relationships.
- Raw OTPs, reset tokens, passwords, income, and delivery addresses must never enter logs or Audit metadata.

```text
Password recovery

Frontend -> Gateway -> Auth Service
                         | lookup account without disclosure
                         | create hashed OTP challenge
                         v
                  Notification internal API -> SMTP

Frontend -> Gateway -> Auth verify OTP -> one-time reset token
Frontend -> Gateway -> Auth confirm reset -> password update + session invalidation
                                      -> sanitized Audit event
                                      -> password-changed email
```

```text
Card application

Customer -> Gateway -> Card Service -> Account internal validation
                         | CARD_APPLICATIONS: PENDING
                         v
                     application event/email

Admin -> Gateway -> Card Service approval
                         | lock application
                         | revalidate account and eligibility
                         | insert BANK_CARDS
                         | mark application APPROVED
                         v
                     card-issued + approval event/email
```

## 4. Scope

### Included

- Public password-reset request by email
- Six-digit email OTP
- Hashed OTP persistence, expiration, resend cooldown, bounded attempts, and lockout
- One-time opaque reset token after OTP verification
- Password-policy enforcement and password-confirmation validation
- Rejection of the current password as the replacement password
- Invalidation of all active sessions after a successful reset
- Password-reset and password-changed email templates
- Sanitized security Audit events
- Customer card application creation, idempotent replay, list, and detail
- Structured delivery-address snapshot
- Annual-income capture
- `DEBIT` applications with `CLASSIC`, `GOLD`, and `PLATINUM` products
- Administrator application list, detail, approve, and reject
- Account ownership/activity validation during application and approval
- Product eligibility validation using environment-backed thresholds
- Atomic card issuance and application approval inside Card Service
- Card-application and card-issued events/emails
- Database constraints and indexes
- Gateway, Swagger, frontend API, internal API, README, and operational documentation updates
- Unit, integration, security, clean-schema, and regression testing

### Not included

- Password change from an already authenticated settings screen
- Username recovery
- SMS or voice OTP
- Security questions
- Magic-link-only password reset
- Password history beyond rejecting the current password
- Device fingerprinting, CAPTCHA, Redis, or a distributed edge-rate-limiting platform
- Credit-card billing, credit limits, statements, minimum payments, interest, or repayment
- Card purchase authorization or merchant processing
- Physical card manufacturing, shipment, delivery tracking, PIN, or CVV
- Customer cancellation or editing of an already submitted card application
- Application document upload or manual income-document verification
- Automated credit underwriting or external bureau integration
- New Report types or Admin dashboard sections for card applications
- Changes to banking Saga ordering, money movement, loan, bill-payment, scheduler, Audit query, Report generation, or Admin aggregation behavior

Before exposing password recovery to the public internet, an infrastructure-level rate limiter and bot protection are recommended. This PRD provides durable per-account cooldown and attempt controls but does not introduce a distributed edge-security product.

## 5. Permitted existing-service changes

### Auth Service

Permitted:

- add password-reset entities, repositories, DTOs, service methods, controller routes, and exception mappings
- add a password mutation method to `AppUser`
- add a case-insensitive email lookup that does not change login behavior
- reuse the existing `PasswordPolicy`, `PasswordEncoder`, and `SessionService`
- call one protected Notification internal endpoint
- publish sanitized reset lifecycle Audit events

Forbidden:

- storing raw OTP or reset token
- returning a JWT from password reset
- automatically logging the user in after reset
- weakening registration/login/2FA/session checks
- revealing whether an email exists

### Notification Service

Permitted:

- add a protected internal template-send API
- add `PASSWORD_RESET_OTP`, `PASSWORD_CHANGED`, `CARD_APPLICATION_RECEIVED`, `CARD_APPLICATION_APPROVED`, and `CARD_APPLICATION_REJECTED` templates
- consume new card application events where required

Forbidden:

- owning reset challenges or card applications
- validating OTPs or reset tokens
- logging template variables containing OTP
- exposing the internal send route through Gateway

### Card Service

Permitted:

- add card-application entity/repository/DTO/service/controller components
- add `CardProduct`
- persist the approved product on `BANK_CARDS`
- reuse current account validation, card PAN encryption, duplicate-card checks, and card-issued event behavior
- replace direct administrative issuance with application approval as the supported issuance path

Forbidden:

- reading Account or Auth tables
- issuing a card before approval is durably committed
- marking an application approved without exactly one linked issued card
- adding credit-card semantics to the existing debit-card model

### Gateway

Permitted:

- add exactly three password-reset paths to the public session-bypass allow-list

Forbidden:

- making any card application route public/unauthenticated
- exposing Notification internal routes
- bypassing session validation for existing protected APIs

## 6. Password-recovery domain

### 6.1 Required flow

The flow has three server-authorized steps:

```text
REQUESTED -> OTP_VERIFIED -> CONSUMED
```

Frontend navigation state is never authorization. Only a valid, unexpired, one-time reset token issued by Auth Service after OTP verification can authorize the password mutation.

### 6.2 Challenge statuses

- `PENDING` — OTP issued and eligible for verification
- `VERIFIED` — OTP accepted; reset token issued
- `CONSUMED` — password changed; challenge cannot be reused
- `EXPIRED` — OTP or reset token expired
- `LOCKED` — maximum failed OTP attempts reached
- `DELIVERY_FAILED` — notification could not be accepted/delivered during request handling

### 6.3 Security rules

- OTP is exactly six decimal digits.
- Generate OTP with `SecureRandom`; never with `Random`, timestamps, or sequential counters.
- Persist only a keyed digest/HMAC of the OTP using a dedicated secret from `.env`.
- Generate the reset token from at least 32 cryptographically random bytes.
- Return the raw reset token only once, after successful OTP verification.
- Persist only a SHA-256 or keyed digest of the reset token.
- Compare digests in constant time.
- OTP lifetime, reset-token lifetime, maximum attempts, and resend cooldown come from `.env` with no YAML defaults.
- A new accepted reset request invalidates previous `PENDING` or `VERIFIED` challenges for the same user.
- OTP verification increments failure count atomically.
- Reaching the maximum attempts marks the challenge `LOCKED`.
- An expired, locked, consumed, delivery-failed, or otherwise invalid challenge cannot be revived.
- Successful confirmation marks the challenge `CONSUMED` in the same transaction as the password update.
- The new password must satisfy the existing registration password policy.
- `newPassword` and `confirmPassword` must match.
- The new password must not match the current password.
- Successful confirmation invalidates every active session for the user.
- Password reset never bypasses or disables 2FA. The next login still requires OTP when 2FA is enabled.

### 6.4 Enumeration resistance

`POST /request` must return the same status, envelope, message, and approximately equivalent server behavior for registered and unregistered addresses.

Required response:

```json
{
  "success": true,
  "message": "If the email is registered, a password reset code will be sent",
  "data": null,
  "timestamp": "2026-08-05T08:30:00Z"
}
```

The API must not reveal:

- whether the email exists
- account status
- user ID or username
- resend cooldown state for an unknown address
- Notification delivery outcome

Known accounts under cooldown also receive the same generic `202`; no second OTP is sent until cooldown ends.

## 7. Password-reset table

Auth Service owns `PASSWORD_RESET_CHALLENGES`.

Minimum fields:

- `challengeId` — generated UUID string, primary key
- `userId` — required local foreign key to `APP_USER.USER_ID`
- `otpDigest` — required; never raw OTP
- `resetTokenDigest` — nullable until verification
- `status`
- `failedAttempts`
- `otpExpiresAt`
- `tokenExpiresAt` — nullable until verification
- `createdAt`
- `lastSentAt`
- `verifiedAt`
- `consumedAt`
- `updatedAt`

Required constraints and indexes:

- Primary key on `CHALLENGE_ID`
- Foreign key `USER_ID -> APP_USER.USER_ID`
- Unique constraint on non-null reset-token digest where supported; otherwise repository-level uniqueness plus a normal unique constraint
- Check `FAILED_ATTEMPTS >= 0`
- Index on `(USER_ID, STATUS, CREATED_AT)`
- Index on `(STATUS, OTP_EXPIRES_AT)`
- Index on `(STATUS, TOKEN_EXPIRES_AT)`

Raw email is not duplicated in this table. The registered address remains owned by `APP_USER`.

Expired rows may be retained for a configured security-retention period and then removed by an Auth-owned cleanup scheduler. Cleanup must never delete `APP_USER` or session data.

## 8. Password-reset public APIs

All three routes are public through Gateway and Auth Security configuration.

### 8.1 Request reset code

```http
POST /api/auth/password-reset/request
Content-Type: application/json
```

```json
{
  "email": "customer@example.com"
}
```

Validation:

- `email` required and syntactically valid
- trim surrounding whitespace
- perform case-insensitive lookup
- never echo the address

Response: `202 Accepted` with the generic enumeration-safe envelope.

Behavior for a known, eligible user:

1. Enforce resend cooldown.
2. Invalidate prior active challenges.
3. Generate and persist hashed OTP challenge.
4. Ask Notification Service to send `PASSWORD_RESET_OTP` to the stored APP_USER email.
5. If Notification accepts the email, retain `PENDING`.
6. If delivery request fails, mark `DELIVERY_FAILED`, log a sanitized operational failure, and still return the generic `202`.

### 8.2 Verify reset code

```http
POST /api/auth/password-reset/verify
Content-Type: application/json
```

```json
{
  "email": "customer@example.com",
  "otpCode": "482193"
}
```

Success: `200 OK`.

```json
{
  "success": true,
  "message": "Password reset code verified",
  "data": {
    "resetToken": "one-time-opaque-token",
    "expiresAt": "2026-08-05T08:40:00Z"
  },
  "timestamp": "2026-08-05T08:30:00Z"
}
```

Failure behavior:

- `400` with one generic message such as `Invalid or expired password reset code`
- do not distinguish nonexistent email, missing challenge, wrong OTP, expired OTP, or locked challenge
- an invalid OTP increments attempts only for a real pending challenge
- verification is safe under concurrent requests; only one request can move `PENDING -> VERIFIED`

### 8.3 Confirm new password

```http
POST /api/auth/password-reset/confirm
Content-Type: application/json
```

```json
{
  "resetToken": "one-time-opaque-token",
  "newPassword": "NewBanking!456",
  "confirmPassword": "NewBanking!456"
}
```

Success: `200 OK`.

```json
{
  "success": true,
  "message": "Password reset successful. Please log in again",
  "data": null,
  "timestamp": "2026-08-05T08:35:00Z"
}
```

The transaction must:

1. Resolve the reset-token digest under a database lock.
2. Require `VERIFIED` and unexpired token.
3. Validate confirmation and password policy.
4. Reject reuse of the current password.
5. Update the APP_USER password hash.
6. Mark the challenge `CONSUMED`.
7. Invalidate all active user sessions.
8. Commit.
9. Publish sanitized `password-reset-completed` and password-changed notification after the business state is stable.

The response must never contain a JWT. The user returns to normal login and must supply 2FA when enabled.

## 9. Password-reset internal notification contract

Add a Notification-owned route that is absent from Gateway:

```http
POST /internal/notifications/email/template
X-Internal-Api-Key: <INTERNAL_API_KEY>
Content-Type: application/json
```

Minimum request fields:

- `recipient`
- `templateName`
- `variables`
- `sourceEvent`
- `referenceId`

For reset OTP, variables contain the raw OTP and formatted expiry duration only for rendering. Notification Service must not log the request body or template variables.

The internal route must:

- reject missing/invalid internal key with `401` or `403` consistently
- use existing template rendering, email persistence, SMTP behavior, and retry history
- return only notification ID/status, not rendered HTML or secrets
- never be exposed through Gateway

Password-reset OTP must not be published on a long-retention Kafka topic. Sanitized reset lifecycle events may use Kafka independently.

## 10. Card-application domain

### 10.1 Card type

This closeout release supports only:

- `DEBIT`

Do not add `CREDIT` to `CardType`. A credit card requires a separate credit-limit, billing, statement, interest, minimum-payment, and repayment domain.

### 10.2 Card products

Add `CardProduct`:

- `CLASSIC`
- `GOLD`
- `PLATINUM`

Product display labels may be supplied by frontend mapping or a future catalog API. The backend enum is the current validation source.

Each product has environment-backed configuration:

- minimum annual income
- default daily transaction limit
- maximum daily transaction limit or the existing global maximum, whichever is lower

Exact business values must be placed in `.env` before implementation and must not be hardcoded in Java or YAML defaults.

### 10.3 Application statuses

- `PENDING`
- `APPROVED`
- `REJECTED`

Allowed transitions:

```text
PENDING -> APPROVED
PENDING -> REJECTED
```

No transition leaves `APPROVED` or `REJECTED`. A customer submits a new application with a new idempotency key after rejection.

### 10.4 Required customer inputs

- `accountId`
- `cardType`
- `annualIncome`
- `cardProduct`
- delivery `addressLine1`
- optional delivery `addressLine2`
- delivery `city`
- delivery `state`
- delivery `country`
- delivery `postalCode`

`accountId` is required even though it was not in the initial four-field request. One APP_USER may own multiple accounts, and Card Service must not guess or silently use the primary account.

The delivery address is an immutable application snapshot. It is intentionally separate from Customer Profile because card delivery may use a different address and later profile edits must not rewrite review history.

### 10.5 Application business rules

Before inserting `PENDING`:

- Authenticated JWT subject becomes `customerUserId`; never accept it from the customer body.
- Account Service confirms the account exists, is `ACTIVE`, and belongs to that customer.
- `cardType` must be `DEBIT`.
- `annualIncome` must be positive and meet the selected product threshold.
- Delivery-address required fields must be nonblank and within field limits.
- No `PENDING` application may already exist for the same customer/account/product.
- No non-expired equivalent card may already exist when the existing per-account card rule would reject issuance.
- `Idempotency-Key` is mandatory.

Income and delivery-address fields are sensitive business data:

- return them only to the owning customer and ADMIN
- never include them in Kafka events, logs, audit metadata, global search, or masked card operational DTOs
- do not copy them into `BANK_CARDS`

## 11. Card-application table

Card Service owns `CARD_APPLICATIONS`.

Minimum fields:

- `applicationId` — generated UUID string, primary key
- `customerUserId` — Auth Service reference
- `accountId` — Account Service reference
- `cardType`
- `cardProduct`
- `annualIncome`
- `addressLine1`
- `addressLine2`
- `city`
- `state`
- `country`
- `postalCode`
- `status`
- `idempotencyKey`
- `requestFingerprint`
- `rejectionReason`
- `reviewNote`
- `reviewedBy` — ADMIN user ID
- `reviewedAt`
- `issuedCardId` — nullable local foreign key to `BANK_CARDS.CARD_ID`
- `createdAt`
- `updatedAt`
- `version` — optimistic locking

Required constraints and indexes:

- Primary key on `APPLICATION_ID`
- Unique `(CUSTOMER_USER_ID, IDEMPOTENCY_KEY)`
- Unique nullable `ISSUED_CARD_ID`
- Local foreign key `ISSUED_CARD_ID -> BANK_CARDS.CARD_ID`
- Check `ANNUAL_INCOME > 0`
- Index `(CUSTOMER_USER_ID, STATUS, CREATED_AT)`
- Index `(STATUS, CREATED_AT)` for the approval queue
- Index `(ACCOUNT_ID, STATUS)` for duplicate/race checks
- Index `(CARD_PRODUCT, STATUS)` for review filtering

Do not add database foreign keys from `CARD_APPLICATIONS.CUSTOMER_USER_ID` to Auth tables or from `ACCOUNT_ID` to Account tables. They cross logical service ownership and are validated through internal APIs.

### `BANK_CARDS` extension

Add required `CARD_PRODUCT` to issued cards and all appropriate Card response/event DTOs. Existing clean-schema test cards must be recreated through the new approval path. If backward-compatible live data must be retained, a separately approved migration/backfill is required.

## 12. Card-application customer APIs

All routes require a valid Gateway-checked JWT session.

### 12.1 Submit application

```http
POST /api/cards/applications
Authorization: Bearer <JWT>
Idempotency-Key: <crypto.randomUUID()>
Content-Type: application/json
```

```json
{
  "accountId": "account-id",
  "cardType": "DEBIT",
  "annualIncome": 750000.00,
  "cardProduct": "GOLD",
  "deliveryAddress": {
    "addressLine1": "12 Banking Street",
    "addressLine2": "Near Central Park",
    "city": "Chennai",
    "state": "Tamil Nadu",
    "country": "India",
    "postalCode": "600001"
  }
}
```

Success: `201 Created`.

Idempotency behavior for `(customerUserId, Idempotency-Key)`:

- Same key and identical normalized request returns the existing application without a duplicate row or email.
- Same key with any changed field returns `409 Conflict`.
- Replay may return the application's current state, including APPROVED or REJECTED.

### 12.2 List applications

```http
GET /api/cards/applications?status=PENDING&page=0&size=20
```

- CUSTOMER sees only own applications.
- ADMIN may omit `customerUserId` for all applications or add `customerUserId`, `status`, `cardProduct`, `page`, and `size` filters.
- Page numbers are zero-based; size range is 1-100.

### 12.3 Application detail

```http
GET /api/cards/applications/{applicationId}
```

Access: owner or ADMIN.

Response fields:

- all application fields safe for the owner/reviewer
- structured delivery address
- `status`
- `rejectionReason`
- `reviewNote`
- `reviewedBy`
- `reviewedAt`
- `issuedCardId`
- timestamps

Another customer's application must return ownership-safe `404` to a CUSTOMER.

## 13. Card-application administrator APIs

Mutation remains in Card Service. ADMIN frontend calls these routes through Gateway.

### 13.1 Approve

```http
PUT /api/cards/applications/{applicationId}/approve
Authorization: Bearer <ADMIN JWT>
Content-Type: application/json
```

```json
{
  "dailyTransactionLimit": 50000.00,
  "reviewNote": "Eligibility verified"
}
```

Rules:

- ADMIN role required.
- Application must be `PENDING` or already `APPROVED` for exact replay.
- Daily limit must be positive and no greater than product/global maximum.
- `reviewNote` optional and bounded.
- Revalidate account ownership and ACTIVE status.
- Revalidate configured income eligibility.
- Recheck duplicate/non-expired card rule while holding the application lock.

Atomic local transaction:

1. Lock application by ID.
2. Reject `REJECTED` with `409`.
3. If already `APPROVED`, return the existing application/card result without issuing again.
4. Validate account and product rules.
5. Generate/encrypt a unique card PAN using existing Card Service logic.
6. Insert exactly one `BANK_CARDS` row with product and `INACTIVE` status.
7. Set `issuedCardId`, reviewer identity/time, note, and `APPROVED`.
8. Commit both records.
9. Publish `card-application-approved` and existing `card-issued` only after commit.

Success: `200 OK` with application review response containing the issued masked Card response.

If validation, downstream Account access, PAN generation, or card persistence fails, the transaction rolls back and the application remains `PENDING`.

### 13.2 Reject

```http
PUT /api/cards/applications/{applicationId}/reject
Authorization: Bearer <ADMIN JWT>
Content-Type: application/json
```

```json
{
  "reason": "Annual income does not meet product eligibility requirements",
  "reviewNote": "Customer may apply for CLASSIC"
}
```

Rules:

- ADMIN role required.
- `reason` required, nonblank, and bounded.
- Only `PENDING` may become `REJECTED`.
- Exact retry of an already rejected application returns its stored result without another email.
- Rejecting an approved application returns `409`.
- Publish rejection event/email only after commit.

Success: `200 OK`.

### 13.3 Direct issuance retirement

The existing public `POST /api/cards` ADMIN issuance endpoint bypasses applications. To make approval authoritative, it must be removed from the supported public contract during this closeout release.

Card issuance must occur only through `PUT /api/cards/applications/{id}/approve`.

The internal Card Service issuance method may remain private application-service logic and be reused by approval. No general `/internal/cards/issue` route is required.

This is an intentional breaking API change and is included in the final approval gate.

## 14. Card application response models

### Customer/application response

- `applicationId`
- `customerUserId`
- `accountId`
- `cardType`
- `cardProduct`
- `annualIncome`
- `deliveryAddress`
- `status`
- `rejectionReason`
- `reviewNote`
- `reviewedBy`
- `reviewedAt`
- `issuedCardId`
- `createdAt`
- `updatedAt`

### Approval response

- `application` — application response
- `card` — standard masked Card response
- no full card PAN, encryption material, or internal Account validation payload

### Issued Card response extension

Add `cardProduct` to:

- card detail
- card list
- card status where product display requires it
- Admin Card operations DTO
- `card-issued` event
- Card Report row output where current reporting serializes Card operational DTOs

Do not add annual income or delivery address to issued Card responses.

## 15. Events and notifications

### Password recovery

Sanitized Audit events:

- `password-reset-requested` — only after a reset email is accepted for a real account
- `password-reset-verification-failed` — bounded/security-relevant failure without email or OTP
- `password-reset-verified`
- `password-reset-completed`

Notification templates:

- `PASSWORD_RESET_OTP`
- `PASSWORD_CHANGED`

The password-reset OTP itself travels only through the protected internal Notification call. It must not appear in Audit, Kafka, logs, exception messages, API responses other than the user's submitted verify body, or database plaintext.

### Card applications

Kafka topics/events:

- `card-application-submitted`
- `card-application-approved`
- `card-application-rejected`
- existing `card-issued` remains authoritative for the created card

Notification templates:

- `CARD_APPLICATION_RECEIVED`
- `CARD_APPLICATION_APPROVED`
- `CARD_APPLICATION_REJECTED`

Events contain stable identifiers and safe fields only:

- `eventId`
- `eventType`
- `applicationId`
- `cardId` when approved
- `actorUserId`
- `customerUserId`
- `cardType`
- `cardProduct`
- `status`
- timestamps
- recipient/template fields only for Notification integration under the existing pattern

Events must not contain annual income, delivery address, account number, full card PAN, rejection free text, review notes, JWT, internal API key, OTP, reset token, or password.

Exact idempotent API replay must not emit a duplicate customer email.

## 16. Response and HTTP status rules

Use the existing shared success envelope:

```json
{
  "success": true,
  "message": "",
  "data": {},
  "timestamp": ""
}
```

Use the existing shared failure envelope:

```json
{
  "success": false,
  "message": "",
  "path": "",
  "timestamp": ""
}
```

Required statuses:

- `200 OK` — reset verification, reset confirmation, reads, approval, rejection, idempotent application replay
- `201 Created` — new card application
- `202 Accepted` — password-reset request, regardless of email existence/delivery disclosure
- `400 Bad Request` — malformed payload, invalid/expired reset code, password mismatch/policy/reuse, invalid income/address/limit
- `401 Unauthorized` — missing/invalid JWT on card routes or invalid Gateway session
- `403 Forbidden` — CUSTOMER attempts approval/rejection or ADMIN-only action
- `404 Not Found` — ownership-safe missing card application or real ADMIN lookup miss
- `409 Conflict` — idempotency payload mismatch, duplicate pending application, invalid terminal transition, approved/rejected conflict, duplicate card
- `503 Service Unavailable` — Account or Notification dependency unavailable where the operation cannot safely proceed
- `500 Internal Server Error` — unexpected defect only

Password-reset request must never return `404` for an unknown email or disclose Notification failure.

Never return:

- JWT after password reset
- raw OTP/reset token in errors or logs
- success body after error status
- approved application without a linked card
- full card PAN
- raw entity objects

## 17. Security and privacy

- Add only the three password-reset routes to Gateway public paths.
- All card application routes require Gateway session validation and local JWT verification.
- ADMIN review uses `@PreAuthorize("hasRole('ADMIN')")` or equivalent method security.
- Customer identity derives from JWT `sub`.
- Reviewer identity derives from ADMIN JWT `sub`.
- Notification internal send requires `X-Internal-Api-Key` and is absent from Gateway.
- OTP digest uses a dedicated HMAC key; do not reuse `JWT_SECRET`, `INTERNAL_API_KEY`, `TWOFA_ENCRYPTION_KEY`, `KYC_ENCRYPTION_KEY`, or `CARD_ENCRYPTION_KEY`.
- Reset tokens are high entropy, one-time, short-lived, and stored only as digest.
- Password hashes use the existing `PasswordEncoder` configuration.
- Successful reset invalidates all server-side sessions.
- Raw OTP, reset token, password, income, address, full card PAN, email, and SMTP/internal secrets must not enter Audit metadata.
- Card application ownership is enforced in repository/service queries, not by trusting request fields.
- Approval obtains a database lock or optimistic version check to prevent double issuance.
- Sensitive values must not appear in `toString()`, exception messages, controller logs, Kafka payloads, or test output.

## 18. Configuration

Add to root `.env` and Compose/service YAML placeholders with no defaults:

- `PASSWORD_RESET_OTP_HMAC_KEY`
- `PASSWORD_RESET_OTP_TTL_SECONDS`
- `PASSWORD_RESET_TOKEN_TTL_SECONDS`
- `PASSWORD_RESET_MAX_ATTEMPTS`
- `PASSWORD_RESET_RESEND_COOLDOWN_SECONDS`
- `PASSWORD_RESET_RETENTION_DAYS`
- `CARD_CLASSIC_MIN_ANNUAL_INCOME`
- `CARD_GOLD_MIN_ANNUAL_INCOME`
- `CARD_PLATINUM_MIN_ANNUAL_INCOME`
- `CARD_CLASSIC_DEFAULT_DAILY_LIMIT`
- `CARD_GOLD_DEFAULT_DAILY_LIMIT`
- `CARD_PLATINUM_DEFAULT_DAILY_LIMIT`

Continue using existing:

- `CARD_MAX_DAILY_LIMIT`
- `CARD_ENCRYPTION_KEY`
- `JWT_SECRET`
- `INTERNAL_API_KEY`
- SMTP configuration
- Kafka bootstrap configuration
- Auth, Notification, Account, and Card service URLs

Kafka topic names remain explicit YAML/application configuration and must not be added to `.env` solely for this feature.

Do not add `service-defaults`, change `Containerfile`, redesign Compose, add Redis, or add new service ports.

## 19. Schema and migration policy

The local development DDL source remains service-owned JPA entities under the existing Hibernate update convention.

Required ownership:

| Table | Owning service |
| --- | --- |
| `PASSWORD_RESET_CHALLENGES` | Auth Service |
| `CARD_APPLICATIONS` | Card Service |
| `APP_USER` password field extension/mutator | Auth Service |
| `BANK_CARDS.CARD_PRODUCT` | Card Service |

Acceptance requires a clean disposable schema test because Hibernate update cannot prove all new check/unique constraints against an old schema.

If this feature is applied to a database containing valuable existing cards, a migration/backfill for `BANK_CARDS.CARD_PRODUCT` must be designed and approved separately. The current user-approved development pattern may instead drop/recreate disposable local tables.

## 20. Testing requirements

### 20.1 Password-reset unit tests

- Secure six-digit OTP generation
- OTP digest and constant-time comparison
- Reset-token generation and digest
- Registered and unregistered email receive identical request status/body
- Case-insensitive email lookup
- Resend cooldown prevents duplicate email/challenge
- New challenge invalidates prior pending/verified challenge
- Valid OTP transitions exactly once to `VERIFIED`
- Wrong OTP increments attempts
- Maximum attempts produces `LOCKED`
- Expired OTP rejected
- Reset token cannot be issued twice concurrently
- Valid reset token changes password
- Password mismatch rejected
- Weak password rejected
- Current password reuse rejected
- Expired token rejected
- Consumed token replay rejected
- Successful reset invalidates all sessions
- Existing 2FA state remains enabled
- Raw secrets absent from logs/events

### 20.2 Password-reset integration tests

- Gateway allows exactly the three reset routes without JWT
- Other protected Auth routes remain protected
- Known configured test email receives six-digit OTP email
- Unknown email returns the same `202` and sends no email
- Correct OTP returns one reset token
- Wrong OTP returns generic `400`
- Confirmation succeeds with valid token
- Old password login fails
- New password login succeeds
- Every pre-reset JWT/session returns `401`
- Reused token fails
- Password-changed email is recorded/sent
- Audit contains sanitized lifecycle events without email/OTP/token/password
- Notification outage produces generic request response without a usable challenge

Use only the configured SMTP sender address as the recipient during integration testing, consistent with project testing guardrails.

### 20.3 Card-application unit tests

- JWT owner is used instead of customer body identity
- Missing/invalid/inactive/wrong-owner account rejected
- Unsupported card type/product rejected
- Zero/negative income rejected
- Below-threshold product rejected
- Incomplete/oversized address rejected
- New application persists `PENDING`
- Exact idempotent replay returns one row
- Changed request with same key returns `409`
- Duplicate pending application rejected
- Existing non-expired equivalent card rule enforced
- Customer lists/sees only own applications
- ADMIN filters applications correctly
- CUSTOMER approval/rejection returns `403`
- Approval revalidates account
- Approval creates exactly one encrypted/masked card
- Approval and issued card commit atomically
- Approval failure leaves application `PENDING`
- Approval replay never issues another card/event/email
- Rejection requires reason
- Rejection replay is stable
- Approved-to-rejected and rejected-to-approved return `409`
- Income/address absent from Kafka/Audit/operational Card DTOs

### 20.4 End-to-end card lifecycle

1. Register/login a customer.
2. Complete profile/KYC and open an active account using existing flows.
3. Submit DEBIT/GOLD card application with idempotency key.
4. Replay and prove one application.
5. Login as ADMIN.
6. List/filter PENDING applications.
7. Approve with valid daily limit.
8. Prove one application and one BANK_CARDS row.
9. Customer views masked card with product and `INACTIVE` state.
10. Customer activates, blocks, unblocks, and updates limit using existing lifecycle APIs.
11. Submit another application and reject it.
12. Customer sees rejection reason.
13. Verify application and card emails using the configured mailbox.
14. Verify sanitized Audit events.

### 20.5 Regression

- Full Maven test reactor passes.
- All Compose containers remain running.
- Registration, login, 2FA, session expiry, logout/logout-all pass.
- Account opening, transfer, deposit, withdrawal, bill payment, loan repayment, and Scheduler flows pass.
- Existing card lifecycle works for an approved card.
- Kafka, Notification, Audit, Report, and Admin Phase 5 tests pass.
- Report Card output includes product but no application income/address.
- Frontend API endpoint inventory is regenerated/cross-checked.

### 20.6 Schema audit

- New tables exist on a clean schema.
- Every new table has a primary key.
- Auth-local and Card-local foreign keys exist.
- Cross-service IDs have no database foreign keys.
- Required unique constraints, checks, and indexes exist.
- No raw OTP/reset token/password/full PAN is stored.
- One approved application links to exactly one issued card.

## 21. Acceptance criteria

This closeout release is complete only when:

- Password-reset request never reveals account existence.
- A real registered email receives a six-digit OTP through Notification Service.
- OTP is stored only as a keyed digest and expires according to `.env`.
- Attempts and cooldown are durable and enforced.
- OTP verification returns a one-time short-lived reset token.
- Password confirmation requires that token and the existing password policy.
- Current-password reuse is rejected.
- Successful reset invalidates all sessions and requires normal login/2FA again.
- Reset secrets never appear in database plaintext, logs, Kafka, Audit, or errors.
- Customer can apply for a DEBIT card against a chosen owned ACTIVE account.
- Application stores product, income, and a structured delivery-address snapshot.
- Idempotent replay cannot duplicate an application or notification.
- ADMIN can list, inspect, approve, and reject applications.
- Approval revalidates account/product rules and atomically creates exactly one card.
- Rejection is final and records a bounded reason.
- Existing public direct card issuance is retired so applications cannot be bypassed.
- Issued cards include product and retain all existing PAN encryption/masking rules.
- Notification and Audit events are delivered without sensitive application data.
- Correct HTTP statuses and shared envelopes are returned.
- Internal Notification route is not reachable through Gateway and rejects invalid keys.
- Documentation matches the final implementation.
- Maven, clean-schema, Compose, full integration, and regression tests pass.
- Changes are committed in small reviewed commits and are not pushed unless explicitly requested.

## 22. Implementation approval gate

Approve these decisions before production code begins:

1. This is a post-Phase-5 closeout release, not Phase 6 and not a new microservice.
2. Password recovery uses three public APIs: request, verify, and confirm.
3. Request always returns generic `202`, whether or not the email exists.
4. OTP is six digits, short-lived, hashed with a dedicated HMAC key, attempt-limited, and cooldown-protected.
5. Successful OTP verification returns a separate one-time reset token; frontend state alone never authorizes password change.
6. Successful reset invalidates all sessions and does not issue a JWT.
7. OTP email uses a protected direct Auth-to-Notification internal API rather than a long-retention Kafka topic.
8. Card application requires `accountId` because one user may have multiple accounts.
9. Delivery address is stored as an application snapshot rather than reused dynamically from Customer Profile.
10. This release supports only DEBIT cards with CLASSIC, GOLD, and PLATINUM products.
11. Product income thresholds and default limits come from `.env` with no hardcoded defaults.
12. Card application creation requires `Idempotency-Key`.
13. Admin approval/rejection is implemented in Card Service public ADMIN routes, not Admin Service and not Workflow Service.
14. Approval creates the card and marks the application approved in one Card Service transaction.
15. Existing public `POST /api/cards` direct issuance is retired; approval becomes the only issuance path.
16. Income/address never enter Audit, Kafka, global search, or issued Card records.
17. No `Containerfile`, service-defaults, Compose redesign, Redis, new service, or unrelated refactor is permitted.

No implementation should begin until these decisions are reviewed and explicitly accepted.

## 23. Delivery sequence

Use one focused commit per verified task. Suggested commit names remain short sentences without task numbers:

1. `Add password recovery`
   - Auth challenge model and secure three-step API
   - Notification internal send/template integration
   - session invalidation and Audit events
   - unit and lifecycle tests

2. `Add card applications`
   - Card application schema/model
   - customer create/list/detail
   - account/product validation and idempotency
   - tests

3. `Add card approvals`
   - ADMIN filters/detail/approve/reject
   - atomic issuance, Card product persistence, events/emails
   - retire direct issue API
   - tests

4. `Document closeout flows`
   - Swagger/OpenAPI
   - `API_DOCUMENTATION.md`
   - `frontend_api_documentation.md`
   - `INTERNAL_API_GUIDE.md`
   - README and operations notes

After the individual commits:

- run the complete Maven reactor
- rebuild/recreate only affected containers first
- run both full feature lifecycles
- run all existing platform regression tests
- inspect database constraints/indexes and sanitized events
- produce a final test report and merge verdict
- do not push unless explicitly requested

