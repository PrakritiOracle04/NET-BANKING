# Internet Banking API & Service Guide

This is the starting point for developers integrating with, operating, or extending the Internet Banking platform.

## 1. Local topology

| Service | Local port | Public gateway prefix | Responsibility |
| --- | ---: | --- | --- |
| API Gateway | 8080 | — | Single public entry point, CORS, request logging, routing |
| Auth Service | 8081 | `/api/auth/**` | Registration, login, JWT issuance, RBAC, sessions |
| 2FA Service | 8082 | `/api/2fa/**` | TOTP enrolment, QR code generation, OTP verification |
| Customer Service | 8083 | `/api/customers/**` | Customer profile creation, retrieval, update |
| Branch Service | 8084 | `/api/branches/**` | Read-only branch directory and IFSC lookup |
| Account Service | 8085 | `/api/accounts/**` | Account lifecycle, balances, and mini statements |
| Beneficiary Service | 8086 | `/api/beneficiaries/**` | Saved transfer destinations and their verification state |
| Transaction Service | 8087 | `/api/transactions/**` | Transaction history, search, and statements |
| Banking Workflow Service | 8088 | `/api/banking/**` | Saga orchestration for deposits, withdrawals, and transfers |
| Notification Service | 8089 | `/api/notifications/**` | SMTP email delivery, templates, delivery history, retries, and Kafka event consumption |
| Shared Kernel | — | — | Shared API response contracts, security constants, password policy |

External clients should call the API Gateway at `http://localhost:8080`. The gateway forwards each public prefix unchanged to its owning service.

```text
Client
  -> API Gateway :8080
       -> Auth :8081       /api/auth/**
       -> 2FA :8082        /api/2fa/**
       -> Customer :8083   /api/customers/**
       -> Branch :8084     /api/branches/**
       -> Account :8085    /api/accounts/**
       -> Beneficiary :8086 /api/beneficiaries/**
       -> Transaction :8087 /api/transactions/**
       -> Workflow :8088   /api/banking/**
       -> Notification :8089 /api/notifications/**
```

## 2. Start the platform

1. Ensure `.env` exists in the repository root and contains the Oracle credentials and shared secrets.
2. Package the services with Maven, then run `podman-compose up -d --build` from the repository root.
3. The compose network connects services by their service names; the existing Oracle container is reached through `host.containers.internal`.
4. Use `podman-compose logs -f <service-name>` if a service does not start.

Health checks are available directly on every service:

| URL |
| --- |
| `http://localhost:8081/actuator/health` |
| `http://localhost:8082/actuator/health` |
| `http://localhost:8083/actuator/health` |
| `http://localhost:8084/actuator/health` |
| `http://localhost:8080/actuator/health` |

Each backend service exposes OpenAPI/Swagger UI at `http://localhost:<port>/swagger-ui`.

## 3. Authentication and response conventions

### JWT

`POST /api/auth/register` and `POST /api/auth/login` are public. All other public business routes require:

```http
Authorization: Bearer <token returned by login>
```

Tokens are signed by Auth Service using `JWT_SECRET`; protected services must use the same value. The default expiry is 30 minutes (`JWT_EXPIRATION_MINUTES`).

### Response envelopes

Successful endpoint responses use:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {},
  "timestamp": "2026-07-26T08:12:44Z"
}
```

Errors use:

```json
{
  "success": false,
  "message": "Invalid username or password",
  "path": "/api/auth/login",
  "timestamp": "2026-07-26T08:12:44Z"
}
```

### Internal service requests

Routes under `/internal/**` are not gateway routes and must not be called by browser/mobile clients. They require the shared header below and are used only for service-to-service work:

```http
X-Internal-Api-Key: <INTERNAL_API_KEY>
```

## 4. Public API reference

Use the gateway base URL below for all routes in this section: `http://localhost:8080`.

### Auth Service

| Method and route | Authentication | What it does | Request body / result |
| --- | --- | --- | --- |
| `POST /api/auth/register` | Public | Creates an `AppUser` with BCrypt password storage and `CUSTOMER` role; then creates the matching customer profile. | Body: `username`, `email`, `phone`, `password`, `fullName`. Returns `userId`, username, email, role, 2FA state. |
| `POST /api/auth/login` | Public | Validates username/email and password. If 2FA is enabled, validates the supplied OTP before issuing a JWT. | Body: `username`, `password`, optional `otpCode`. Returns token, `Bearer` token type, expiry, username, role, 2FA state. |
| `POST /api/auth/logout` | Bearer JWT | Invalidates active server-side sessions for the authenticated user. | No body. |
| `GET /api/auth/me` | Bearer JWT | Returns the authenticated user's ID, username, email, and role. | No body. |

Registration validation: username is 3–60 characters; email must be valid; phone is 7–15 digits with optional leading `+`; password requires at least 8 characters including uppercase, lowercase, number, and special character.

Example registration:

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "gokul_test",
  "email": "gokul_test@example.com",
  "phone": "+919876543210",
  "password": "Banking!123",
  "fullName": "Gokul Test"
}
```

### 2FA Service

| Method and route | Authentication | What it does | Request body / result |
| --- | --- | --- | --- |
| `POST /api/2fa/setup` | Bearer JWT | Creates/replaces a disabled TOTP factor and returns setup material. | No body. Returns `secret`, `otpauthUri`, `qrCodeBase64`, issuer, account name, `enabled:false`. |
| `POST /api/2fa/verify-setup` | Bearer JWT | Verifies the authenticator-app code and enables 2FA. | Body: `{ "otpCode": "123456" }`. |
| `POST /api/2fa/verify` | Bearer JWT | Verifies a current OTP without changing setup state. | Body: `{ "otpCode": "123456" }`. |
| `POST /api/2fa/disable` | Bearer JWT | Disables the active factor after OTP verification. | Body: `{ "otpCode": "123456" }`. |
| `GET /api/2fa/status` | Bearer JWT | Returns whether 2FA is enabled for the current user. | No body. |

`qrCodeBase64` is a Base64-encoded PNG. Decode it or display it as `data:image/png;base64,<qrCodeBase64>` to scan it with Google Authenticator, Microsoft Authenticator, or a compatible TOTP app. Never log or persist the returned `secret` in a client application.

### Customer Service

| Method and route | Authentication | What it does | Request body / result |
| --- | --- | --- | --- |
| `GET /api/customers/me` | Bearer JWT | Returns the current user's customer profile. | No body. |
| `PUT /api/customers/me` | Bearer JWT | Updates the current user's personal/contact/address fields. | `fullName`, `phone`, optional `addressLine1`, `addressLine2`, `city`, `state`, `country`, `postalCode`. |
| `GET /api/customers/{id}` | Bearer JWT with `ADMIN` role | Retrieves a customer profile by customer ID. | No body. |

Customer profiles include `customerId`, `userId`, full name, email, phone, address fields, `kycStatus`, and `profileStatus`. Email is created from the Auth registration and is not changed by the profile update API.

### Branch Service

| Method and route | Authentication | What it does |
| --- | --- | --- |
| `GET /api/branches` | Bearer JWT | Lists branch summaries. |
| `GET /api/branches/ifsc/{ifsc}` | Bearer JWT | Finds a branch by IFSC. IFSC format is `^[A-Z]{4}0[A-Z0-9]{6}$`. |
| `GET /api/branches/{id}` | Bearer JWT | Finds a branch by branch ID. |

Branch results contain `branchId`, `branchName`, `ifsc`, `city`; individual lookups also include `state`.

### Account, beneficiary, and transaction services

| Route family | Authentication | What it does |
| --- | --- | --- |
| `GET, POST /api/accounts` and `GET /api/accounts/{id}` | Bearer JWT | Lists, creates, and retrieves accounts. |
| `GET /api/accounts/{id}/balance` | Bearer JWT | Returns the current balance. |
| `GET /api/accounts/{id}/mini-statement` | Bearer JWT | Returns recent account activity. Account Service requests the read-only recent-transaction data from Transaction Service. |
| `PUT /api/accounts/{id}/status` | Bearer JWT with `ADMIN` role | Updates an account's status. |
| `GET, POST /api/beneficiaries`, `GET /api/beneficiaries/{id}` | Bearer JWT | Lists, creates, and retrieves the current customer's beneficiaries. |
| `PUT, DELETE /api/beneficiaries/{id}` | Bearer JWT | Updates or removes a beneficiary owned by the current customer. |
| `PUT /api/beneficiaries/{id}/status` | Bearer JWT with `ADMIN` role | Changes beneficiary verification status. |
| `GET /api/transactions/**` | Bearer JWT | Retrieves transaction history, a transaction by ID, account history, filtered search results, or statements. |

Customer ownership is persisted and authorized using the immutable Auth `userId` from the JWT subject. Usernames remain display/login attributes only. Admin account creation and filtering use `customerUserId`; account, beneficiary, transaction, and workflow records store `CUSTOMER_USER_ID`.

### Banking Workflow Service (Saga-backed money operations)

These are the only public routes that move money. Every request must include a new, client-generated `Idempotency-Key` value. Reuse the same key only to retry the exact same request after a timeout: a completed workflow returns the original result, while a failed/compensated workflow returns `409 Conflict` so the client must use a new key.

```http
Authorization: Bearer <token>
Idempotency-Key: 6c0b9bca-7b04-46cb-8fb0-0eb951afc8ef
```

| Method and route | What it does | Body |
| --- | --- | --- |
| `POST /api/banking/deposit` | Credits an owned active account and records a credit transaction. | `accountId`, `amount`, optional `description` |
| `POST /api/banking/withdraw` | Debits an owned active account and records a debit transaction. | `accountId`, `amount`, optional `description` |
| `POST /api/banking/transfer` | Validates ownership, destination account, and verified beneficiary; debits source, credits destination, then records both transactions. | `sourceAccountId`, `destinationAccountNumber`, `amount`, optional `description` |

The workflow is the sole coordinator: it calls Account, Beneficiary, and Transaction services directly. None of those services calls the next service in a money-moving workflow.

### Notification Service

Notification Service is the platform's only SMTP client. Other services must publish notification events to Kafka or use the Notification Service API; they must never connect to SMTP directly.

| Method and route | Authentication | What it does |
| --- | --- | --- |
| `POST /api/notifications/email/send` | Bearer JWT | Renders a named template with supplied variables, attempts SMTP delivery, and stores delivery state. Body: `recipient`, `templateName`, optional `variables`, `sourceEvent`, `referenceId`. |
| `POST /api/notifications/email/test` | Bearer JWT | Sends a test email using the generic template. Body: `recipient`, optional `variables`. |
| `POST /api/notifications/email/test-kafka` | Bearer JWT | Publishes a test event to `transaction-created`; Notification Service then consumes it and sends the generic email asynchronously. Body: `recipient`, optional `variables`. Returns the Kafka test reference. |
| `GET /api/notifications/email/{id}` | Bearer JWT | Returns a notification's recipient, subject, status, retry count, and timestamps. |
| `GET /api/notifications/email/history` | Bearer JWT | Returns persisted notification history, newest first. |
| `POST /api/notifications/email/{id}/retry` | Bearer JWT | Starts a manual retry for a notification that was not sent. |
| `GET /api/notifications/email/failed` | Bearer JWT | Lists notifications in `FAILED` state. |
| `GET /api/notifications/email/pending` | Bearer JWT | Lists notifications in `PENDING` state. |

Email status values are `PENDING`, `PROCESSING`, `SENT`, `FAILED`, and `RETRYING`. Initial reusable templates are `WELCOME`, `LOGIN_ALERT`, `PASSWORD_RESET`, and `GENERIC_NOTIFICATION`. Templates support placeholders such as `{{customerName}}`, `{{currentTime}}`, `{{verificationLink}}`, and `{{message}}`, and render both HTML and plain-text bodies.

## 5. Service dependencies and flows

| Calling service | Called service | Internal route | Why it is needed |
| --- | --- | --- | --- |
| Auth | Customer | `POST http://localhost:8083/internal/customers` | Creates the customer profile during registration. |
| Auth | 2FA | `GET http://localhost:8082/internal/twofa/users/{userId}/status` | Determines whether login requires an OTP. |
| Auth | 2FA | `POST http://localhost:8082/internal/twofa/verify` | Verifies the OTP as part of a 2FA-enabled login. |
| Workflow | Account | `GET/POST http://localhost:8085/internal/accounts/**` | Validates accounts, applies idempotent balance movements, and reverses movements during compensation. |
| Workflow | Beneficiary | `POST http://localhost:8086/internal/beneficiaries/verify-transfer` | Ensures a transfer destination is a verified beneficiary. |
| Workflow | Transaction | `POST http://localhost:8087/internal/transactions/**` | Records transactions and marks any recorded transaction `REVERSED` during compensation. |
| Kafka producers | Notification | Kafka topics such as `registration-success`, `login-alert`, and `transaction-created` | Delivers asynchronous notification events. Events must include a recipient address before an email can be sent. |

### Registration flow

```text
POST /api/auth/register -> Gateway -> Auth
Auth stores AppUser and role assignment
Auth -> Customer internal create (X-Internal-Api-Key)
Customer stores CustomerProfile
After commit: Auth -> Kafka registration-success -> Notification -> WELCOME email
Auth returns 201 Created
```

Customer Service must be running before registration. If it is unavailable, Auth cannot complete the profile-creation dependency and registration returns an error.

### Login flow

```text
POST /api/auth/login -> Gateway -> Auth
Auth validates BCrypt password
Auth -> 2FA internal status
If enabled: Auth -> 2FA internal verify (otpCode required)
Auth creates UserSession
After commit: Auth -> Kafka login-alert -> Notification -> LOGIN_ALERT email
Auth returns JWT
```

2FA Service must be running for login because Auth always checks 2FA status.

### Saga workflow and compensation

```text
Gateway -> Workflow -> Account (debit/credit) -> Transaction
                         |
                         +-> Beneficiary verification (transfer only)

If a later step fails:
Workflow -> Transaction reverse (when recorded)
         -> Account reverse destination movement (transfer only)
         -> Account reverse source movement
```

`BANKING_WORKFLOWS` stores each workflow's idempotency key, state, references, and compensation progress. `ACCOUNT_MOVEMENTS` stores every applied account movement and its reversal state. This makes a new Saga route extensible: add its type and steps to Workflow Service, persist each successful step before the next remote call, and add its compensating action in reverse order. `COMPENSATION_PENDING` workflows retry on the Workflow Service scheduler; clients receive `503 Service Unavailable` until recovery succeeds.

### Notification flow

```text
Normal notification:
Producer Service -> Kafka -> Notification Service -> Template Renderer -> SMTP -> Recipient

Manual or test notification:
Client -> Gateway -> Notification Service -> Template Renderer -> SMTP -> Recipient
```

Kafka handling is asynchronous: a producer never waits for SMTP delivery. Notification Service writes an `EMAIL_NOTIFICATION` record, attempts delivery, and records every attempt in `EMAIL_DELIVERY_LOG`. Temporary failures enter `RETRYING` and are retried by the Notification Service scheduler. SMTP credentials stay only in the ignored `.env` file.

## 6. Data ownership

Each service owns its own entity model and database tables; no service reads another service repository or table.

| Service | Owned entities |
| --- | --- |
| Auth | `AppUser`, `Role`, `UserSession` |
| 2FA | `AuthFactor` |
| Customer | `CustomerProfile` |
| Branch | `Branch` |
| Account | `Account`, `AccountMovement` |
| Beneficiary | `Beneficiary` |
| Transaction | `BankTransaction` |
| Banking Workflow | `WorkflowSaga` (`BANKING_WORKFLOWS`) |
| Notification | `EmailNotification` (`EMAIL_NOTIFICATION`), `EmailTemplate` (`EMAIL_TEMPLATE`), `EmailDeliveryLog` (`EMAIL_DELIVERY_LOG`) |

`legacy-entity-reference/` retains the original entity files as reference material only. It is outside a Maven source root and is not compiled by Phase 1.

## 7. Configuration needed by a developer

Keep secrets only in the ignored `.env` file.

| Variable | Used by | Notes |
| --- | --- | --- |
| `AUTH_DB_URL`, `AUTH_DB_USERNAME`, `AUTH_DB_PASSWORD` | Auth | Oracle connection |
| `TWOFA_DB_URL`, `TWOFA_DB_USERNAME`, `TWOFA_DB_PASSWORD` | 2FA | Oracle connection |
| `CUSTOMER_DB_URL`, `CUSTOMER_DB_USERNAME`, `CUSTOMER_DB_PASSWORD` | Customer | Oracle connection |
| `BRANCH_DB_URL`, `BRANCH_DB_USERNAME`, `BRANCH_DB_PASSWORD` | Branch | Oracle connection |
| `JWT_SECRET` | Auth, 2FA, Customer, Branch | Same strong Base64 secret in all protected services |
| `INTERNAL_API_KEY` | Auth, 2FA, Customer | Same non-default secret for internal routes |
| `TWOFA_ENCRYPTION_KEY` | 2FA | Separate Base64 256-bit AES key for encrypted TOTP secrets |
| `AUTH_SERVICE_URL`, `TWOFA_SERVICE_URL`, `CUSTOMER_SERVICE_URL`, `BRANCH_SERVICE_URL` | Gateway / Auth | Optional overrides for non-local deployments |
| `CORS_ALLOWED_ORIGINS` | Gateway | Optional allowed frontend origin; defaults to `http://localhost:3000` |
| `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USERNAME`, `NOTIFICATION_DB_PASSWORD` | Notification | Oracle connection for notification-owned tables. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Notification | SMTP transport connection. Never commit these values. |
| `SMTP_FROM_EMAIL`, `SMTP_FROM_NAME`, `SMTP_AUTH`, `SMTP_STARTTLS_ENABLE` | Notification | Sender identity and SMTP security settings. |
| `NOTIFICATION_RETRY_DELAY_MS`, `NOTIFICATION_MAX_RETRIES` | Notification | Retry scheduler interval and retry limit. |
| `KAFKA_BOOTSTRAP_SERVERS` | Auth, Workflow, Notification | Kafka bootstrap connection. Compose configures these services to use the internal listener at `kafka:29092`; host-side tools use `localhost:9092`. |

## 8. Common troubleshooting

| Symptom | Likely cause / action |
| --- | --- |
| `An unexpected error occurred` on registration | Verify Customer Service is running and inspect `logs/auth-service.error.log` and `logs/customer-service.error.log`. |
| Login fails after enabling 2FA | Supply a current six-digit `otpCode` from the enrolled authenticator app; verify 2FA Service is running. |
| `401 Unauthorized` on a business route | Send `Authorization: Bearer <token>` and confirm all protected services share the same `JWT_SECRET`. |
| Internal call rejects its key | Ensure Auth, 2FA, and Customer use exactly the same `INTERNAL_API_KEY`. |
| Duplicate registration error | Choose a new username and email; both must be unique in Auth Service. |
| JAR starts but database actions fail | Confirm Oracle is running and the relevant `*_DB_*` values in `.env` point to the expected service/database. |
| Notification status is `FAILED` or `RETRYING` | Check SMTP host, port, app password, sender address, TLS settings, and outbound port reachability. For Gmail, use a Google App Password rather than the account password. |
| Notification Service cannot consume Kafka events | Confirm the Compose Kafka service is healthy and Auth, Workflow, and Notification use `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`. Kafka UI is available at `http://localhost:8081`. |
