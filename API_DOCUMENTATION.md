# Phase 1 API & Service Guide

This is the starting point for developers integrating with, operating, or extending the Phase 1 Internet Banking platform.

## 1. Local topology

| Service | Local port | Public gateway prefix | Responsibility |
| --- | ---: | --- | --- |
| API Gateway | 8080 | — | Single public entry point, CORS, request logging, routing |
| Auth Service | 8081 | `/api/auth/**` | Registration, login, JWT issuance, RBAC, sessions |
| 2FA Service | 8082 | `/api/2fa/**` | TOTP enrolment, QR code generation, OTP verification |
| Customer Service | 8083 | `/api/customers/**` | Customer profile creation, retrieval, update |
| Branch Service | 8084 | `/api/branches/**` | Read-only branch directory and IFSC lookup |
| Shared Kernel | — | — | Shared API response contracts, security constants, password policy |

External clients should call the API Gateway at `http://localhost:8080`. The gateway forwards each public prefix unchanged to its owning service.

```text
Client
  -> API Gateway :8080
       -> Auth :8081       /api/auth/**
       -> 2FA :8082        /api/2fa/**
       -> Customer :8083   /api/customers/**
       -> Branch :8084     /api/branches/**
```

## 2. Start the platform

1. Ensure `.env` exists in the repository root and contains the Oracle credentials and shared secrets.
2. Double-click `run-all-services.cmd`, or run `./run-all-services.cmd` from the root terminal.
3. The launcher discovers JDK 17 when `JAVA_HOME` is not set, builds the Maven modules, and starts all five services.
4. Review `logs/<service>.log` and `logs/<service>.error.log` if a service does not start.

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

## 5. Service dependencies and flows

| Calling service | Called service | Internal route | Why it is needed |
| --- | --- | --- | --- |
| Auth | Customer | `POST http://localhost:8083/internal/customers` | Creates the customer profile during registration. |
| Auth | 2FA | `GET http://localhost:8082/internal/twofa/users/{userId}/status` | Determines whether login requires an OTP. |
| Auth | 2FA | `POST http://localhost:8082/internal/twofa/verify` | Verifies the OTP as part of a 2FA-enabled login. |

### Registration flow

```text
POST /api/auth/register -> Gateway -> Auth
Auth stores AppUser and role assignment
Auth -> Customer internal create (X-Internal-Api-Key)
Customer stores CustomerProfile
Auth returns 201 Created
```

Customer Service must be running before registration. If it is unavailable, Auth cannot complete the profile-creation dependency and registration returns an error.

### Login flow

```text
POST /api/auth/login -> Gateway -> Auth
Auth validates BCrypt password
Auth -> 2FA internal status
If enabled: Auth -> 2FA internal verify (otpCode required)
Auth creates UserSession and returns JWT
```

2FA Service must be running for login because Auth always checks 2FA status.

## 6. Data ownership

Each service owns its own entity model and database tables; no service reads another service repository or table.

| Service | Owned entities |
| --- | --- |
| Auth | `AppUser`, `Role`, `UserSession` |
| 2FA | `AuthFactor` |
| Customer | `CustomerProfile` |
| Branch | `Branch` |

`legacy-entity-reference/` retains the original entity files as reference material only. It is outside a Maven source root and is not compiled by Phase 1.

## 7. Configuration needed by a developer

Keep secrets only in the ignored `.env` file. Use `.env.example` as the shareable template.

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

## 8. Common troubleshooting

| Symptom | Likely cause / action |
| --- | --- |
| `An unexpected error occurred` on registration | Verify Customer Service is running and inspect `logs/auth-service.error.log` and `logs/customer-service.error.log`. |
| Login fails after enabling 2FA | Supply a current six-digit `otpCode` from the enrolled authenticator app; verify 2FA Service is running. |
| `401 Unauthorized` on a business route | Send `Authorization: Bearer <token>` and confirm all protected services share the same `JWT_SECRET`. |
| Internal call rejects its key | Ensure Auth, 2FA, and Customer use exactly the same `INTERNAL_API_KEY`. |
| Duplicate registration error | Choose a new username and email; both must be unique in Auth Service. |
| JAR starts but database actions fail | Confirm Oracle is running and the relevant `*_DB_*` values in `.env` point to the expected service/database. |
