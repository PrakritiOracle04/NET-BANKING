# Frontend API Documentation

> Source of truth: the current gateway routes, public controllers, request DTOs, validation annotations, security rules, and service behavior in this repository. This document is intentionally independent of every pre-existing frontend API document.

Last verified against the `phase5` branch on 2026-08-05.

## 1. Purpose and audience

This is the frontend integration contract for the Internet Banking platform. It is written for engineers building browser, mobile, desktop, or API-client applications against the public API Gateway.

It covers:

- every currently gateway-exposed API;
- authentication, server-side session validation, roles, and 2FA;
- request headers, path variables, query parameters, JSON payloads, and validation rules;
- successful response formats and domain response fields;
- status-code and error-handling expectations;
- ownership and ADMIN-versus-CUSTOMER behavior;
- mandatory and optional idempotency handling;
- asynchronous report creation, polling, and binary download;
- recommended frontend state-management and retry patterns;
- APIs that exist for diagnostics but should not normally appear in a production customer UI.

Internal `/internal/**` service endpoints are deliberately not frontend APIs. The frontend must never call them or receive `INTERNAL_API_KEY`. Public clients always call the Gateway, and the Gateway or backend services perform internal service-to-service calls.

## 2. Connection contract

### 2.1 Base URL

Local development:

```text
http://localhost:8080
```

Every URL in this document is relative to that base URL. Configure the base URL once in the frontend, for example:

```ts
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
```

Do not call individual service ports from frontend code. Direct service calls bypass Gateway session enforcement, central CORS behavior, and the supported public routing contract.

### 2.2 Content type

For JSON requests:

```http
Content-Type: application/json
```

JSON `GET` and `DELETE` requests normally have no body. Report downloads return binary CSV or PDF content rather than JSON.

### 2.3 Authentication header

Every `/api/**` endpoint except registration and login requires:

```http
Authorization: Bearer <jwt>
```

The token returned by login contains a `sid` session identifier. The Gateway validates the token and the corresponding server-side session on every protected API request. A cryptographically valid JWT is not sufficient if its session was logged out, invalidated, expired, belongs to an inactive user, or no longer matches the user's current role.

Public endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`

All other endpoints require an active session, including branch lookup and loan EMI calculation.

### 2.4 Session behavior

- A successful login creates a new server-side session and returns a JWT.
- The JWT and session share the configured expiration time.
- `POST /api/auth/logout` invalidates only the session represented by the current token.
- `POST /api/auth/logout-all` invalidates every active session for the current user, including the token making the request.
- An expired or invalidated session causes protected Gateway calls to return `401`.
- If Auth Service cannot validate sessions, the Gateway returns `503`; this is not equivalent to logout.
- A role change invalidates old role claims because session validation compares token roles with the current database role.

Frontend rule: on `401`, clear the current authentication state and navigate to login. On `503` with `"Authentication service is unavailable"`, retain local auth state and show a retryable service-unavailable screen instead of falsely logging the user out.

### 2.5 Roles

Current role values:

```text
CUSTOMER
ADMIN
```

An endpoint marked `ADMIN` requires `ROLE_ADMIN`; an authenticated CUSTOMER receives `403`. Ownership-scoped endpoints allow CUSTOMER users to see only their own resources. ADMIN users may receive broader data or use an optional customer filter, as documented per endpoint.

### 2.6 Date, time, money, and identifiers

- `Instant` values use ISO-8601 timestamps, preferably UTC: `2026-08-05T08:30:00Z`.
- `LocalDate` values use `YYYY-MM-DD`: `2026-08-05`.
- Time-zone names use IANA identifiers such as `Asia/Kolkata`, not informal abbreviations such as `IST`.
- Monetary request values must be positive decimal JSON numbers unless an endpoint explicitly allows zero.
- JavaScript binary floating-point is unsuitable for financial arithmetic. Keep display/calculation values in a decimal library and serialize a validated decimal representation.
- IDs are opaque strings. Do not derive meaning from their format and do not substitute account numbers for account IDs.
- Enum values are case-sensitive uppercase strings.

## 3. Standard response and error contracts

### 3.1 Standard success envelope

Most Phase 1-4 APIs return:

```json
{
  "success": true,
  "message": "Human-readable operation result",
  "data": {},
  "timestamp": "2026-08-05T08:30:00Z"
}
```

Use `data` for application state. Do not branch application logic on the human-readable `message`.

### 3.2 Raw Phase 5 responses

Audit, Report, and Admin endpoints return their DTO directly, without the `ApiResponse` envelope. Their examples are explicitly labelled in this document.

### 3.3 Standard error envelope

Gateway and most services return:

```json
{
  "success": false,
  "message": "Invalid request",
  "path": "/api/example",
  "timestamp": "2026-08-05T08:30:00Z"
}
```

Some framework-generated Phase 5 errors may include Spring's standard fields (`timestamp`, `status`, `error`, `path`). A robust client should primarily use the HTTP status and accept either body shape.

### 3.4 Status-code policy

| Status | Frontend meaning | Recommended action |
|---|---|---|
| `200 OK` | Successful synchronous read/update/action | Consume response |
| `201 Created` | Resource/workflow created | Consume body; update or invalidate relevant cache |
| `202 Accepted` | Report job queued asynchronously | Store job ID and poll |
| `204 No Content` | Deletion/deactivation succeeded | Do not parse JSON |
| `400 Bad Request` | Invalid JSON, validation failure, invalid state, missing mandatory public header | Show actionable validation/domain message |
| `401 Unauthorized` | Missing/invalid/expired session or bad login credentials | Login errors stay on form; protected-call errors clear auth |
| `403 Forbidden` | Authenticated but not allowed or does not own resource | Show access-denied state; do not retry |
| `404 Not Found` | Resource absent; ownership-safe services may also conceal another user's resource as `404` | Remove stale local entity or show not found |
| `409 Conflict` | Duplicate, illegal transition, idempotency collision, or operation already in progress | Reconcile state; never blindly generate a new key for a money movement |
| `410 Gone` | Generated report expired | Queue a replacement report |
| `500 Internal Server Error` | Unexpected backend defect | Show generic failure and correlation context if available |
| `502 Bad Gateway` | Scheduler downstream failure | Retry only according to product policy |
| `503 Service Unavailable` | Auth/downstream unavailable, workflow compensation pending, or Admin core unavailable | Preserve request/key and retry safely |

### 3.5 Spring page response

Transaction and bill-payment customer endpoints return a Spring `Page` under `data`:

```json
{
  "content": [],
  "number": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "empty": true
}
```

Admin, Audit, and Report history APIs use a compact page DTO:

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "totalElements": 0,
  "totalPages": 0
}
```

Page numbers are zero-based.

## 4. Idempotency contract

### 4.1 Why it matters

Money movement and multi-service orchestration may complete even if the browser loses the response. Retrying with a new request identity could apply the operation twice. An `Idempotency-Key` identifies one logical user action across retries.

### 4.2 Frontend generation rule

Use a high-entropy unique value such as `crypto.randomUUID()`:

```ts
const idempotencyKey = crypto.randomUUID();
```

Generate the key when the user confirms the action, before making the first request. Persist it with the pending UI operation. Reuse that exact key when retrying the exact same payload after a timeout, connection loss, `502`, or `503`.

Never:

- generate a fresh key for an automatic retry;
- reuse a key after editing amount, account, beneficiary, biller, loan, description, account type, IFSC, report type, format, or filters;
- share one key between different users or workflow types;
- use timestamps, sequential IDs, or a constant key.

### 4.3 Mandatory versus optional

Mandatory for all six Banking Workflow commands:

- account opening;
- transfer;
- bill payment;
- loan repayment;
- deposit;
- withdrawal.

Optional but strongly recommended for every report queue request. Other current public APIs do not consume this header.

### 4.4 Workflow replay behavior

For the same authenticated owner, workflow type, key, and logically identical payload:

- a completed workflow returns the existing completed result without applying the operation again;
- an in-progress, failed, or otherwise non-replayable workflow normally returns `409`;
- `COMPENSATION_PENDING` returns `503` and must be treated as unresolved, not failed-and-safe-to-repeat;
- account opening may resume a compatible non-terminal workflow with the same key;
- using the same key with a different payload returns `409`.

Frontend rule: after an ambiguous transport failure, retry the same request and same key. If the server returns `409` or `503`, stop automated retries and surface an “operation is being reconciled” state; do not submit a replacement money movement.

### 4.5 Report replay behavior

Report idempotency is scoped to the requesting user. Reusing the same key and identical report request returns the original `reportJobId` with `idempotentReplay: true`, regardless of whether its current state is `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, or `EXPIRED`. Reusing the key with a different request fingerprint returns `409`.

## 5. Recommended frontend HTTP client

```ts
type ApiErrorBody = {
  success?: false;
  message?: string;
  path?: string;
  timestamp?: string;
  status?: number;
  error?: string;
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody | null,
  ) {
    super(body?.message ?? body?.error ?? `Request failed (${status})`);
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 204) return undefined as T;
  const isJson = response.headers.get("content-type")?.includes("application/json");
  const body = isJson ? await response.json() : null;
  if (!response.ok) throw new ApiError(response.status, body);
  return body as T;
}
```

For report downloads, use a separate helper that calls `response.blob()` and reads `Content-Disposition`; do not use the JSON helper.

## 6. Authentication APIs

### 6.1 Register

`POST /api/auth/register` — Public — success `201`

Creates an APP_USER with CUSTOMER role, creates the initial customer profile, and publishes registration notification/audit events.

Request:

```json
{
  "username": "gokul_test",
  "email": "gokul_test@example.com",
  "phone": "+919876543210",
  "password": "Banking!123",
  "fullName": "Gokul Test"
}
```

Validation:

| Field | Required | Rules |
|---|---:|---|
| `username` | Yes | 3-60 characters; unique |
| `email` | Yes | Valid email; unique |
| `phone` | Yes | Optional leading `+`, followed by 7-15 digits |
| `password` | Yes | Minimum 8 characters and must contain uppercase, lowercase, number, and special character |
| `fullName` | Yes | Nonblank, maximum 120 characters |

`data` fields: `userId`, `username`, `email`, `role`, `twoFactorEnabled`. New users return `role: "CUSTOMER"` and `twoFactorEnabled: false`.

Expected errors: `400` password/validation failure, `409` duplicate username/email, `500` infrastructure failure.

### 6.2 Login

`POST /api/auth/login` — Public — success `200`, `Cache-Control: no-store`

The `username` field accepts either username or email.

Without enabled 2FA:

```json
{
  "username": "gokul_test",
  "password": "Banking!123"
}
```

With enabled 2FA:

```json
{
  "username": "gokul_test",
  "password": "Banking!123",
  "otpCode": "123456"
}
```

`data` fields:

| Field | Meaning |
|---|---|
| `token` | JWT for the Authorization header |
| `tokenType` | `Bearer` |
| `expiresAt` | Absolute session/token expiry time |
| `username` | Canonical username |
| `role` | `CUSTOMER` or `ADMIN` |
| `twoFactorEnabled` | Current server-side 2FA state |

Expected errors: `400` when OTP is required/invalid, `401` invalid username/password. Login failures do not return a token.

### 6.3 Current authenticated user

`GET /api/auth/me` — Authenticated — success `200`

`data`: `userId`, `username`, `email`, `role`.

Use this endpoint during application bootstrap to hydrate identity after restoring a token. It does not return phone, customer profile, or 2FA status; use the appropriate domain endpoints for those.

### 6.4 Logout current session

`POST /api/auth/logout` — Authenticated — success `200`

No request body. Invalidates only the current token's `sid`. Clear the token locally after a successful response. If the request returns `401` because the session is already invalid, still clear local auth state.

### 6.5 Logout all sessions

`POST /api/auth/logout-all` — Authenticated — success `200`

No request body. Invalidates all sessions for the user on every device. Clear the current token immediately after success.

## 7. Two-factor authentication APIs

All endpoints require an active session. TOTP uses SHA-1, six digits, a 30-second period, and accepts the current time window plus one window before/after.

### 7.1 Start or replace setup

`POST /api/2fa/setup` — Authenticated — success `200`

No body. Calling setup again replaces the prior secret and disables 2FA until the new secret is verified.

`data` fields: `secret`, `otpauthUri`, `qrCodeBase64`, `issuer`, `accountName`, `enabled`.

Render the QR image as:

```ts
const qrSrc = `data:image/png;base64,${response.data.qrCodeBase64}`;
```

Treat `secret`, `otpauthUri`, and `qrCodeBase64` as sensitive. Keep them only in transient setup state and never log or persist them.

### 7.2 Confirm setup

`POST /api/2fa/verify-setup` — Authenticated — success `200`

```json
{ "otpCode": "123456" }
```

Returns `data.enabled: true`. A wrong code returns `400`. Setup does not protect login until this call succeeds.

### 7.3 Verify an OTP

`POST /api/2fa/verify` — Authenticated — success `200`

```json
{ "otpCode": "123456" }
```

Returns `data.enabled: true` when the factor is enabled and the code is valid. This endpoint verifies a code for an already authenticated session; login performs its own internal OTP verification.

### 7.4 Disable 2FA

`POST /api/2fa/disable` — Authenticated — success `200`

```json
{ "otpCode": "123456" }
```

Returns `data.enabled: false`. A current valid code is required.

### 7.5 Get status

`GET /api/2fa/status` — Authenticated — success `200`

Returns `{ "enabled": boolean }` under `data`. Users without any setup receive `false`.

## 8. Customer profile and KYC APIs

### 8.1 Get own profile

`GET /api/customers/me` — Authenticated — success `200`

`data` fields: `customerId`, `userId`, `fullName`, `fatherOrSpouseName`, `dateOfBirth`, `addressLine1`, `addressLine2`, `city`, `state`, `country`, `postalCode`, `profileStatus`.

Immediately after registration, some extended fields may be null until profile completion.

### 8.2 Update own profile

`PUT /api/customers/me` — Authenticated — success `200`

```json
{
  "fullName": "Gokul Krishnan",
  "fatherOrSpouseName": "Parent Name",
  "dateOfBirth": "2000-01-15",
  "addressLine1": "12 Banking Street",
  "addressLine2": "Near Central Park",
  "city": "Chennai",
  "state": "Tamil Nadu",
  "country": "India",
  "postalCode": "600001"
}
```

`fullName`, `fatherOrSpouseName`, `addressLine1`, `city`, `state`, `country`, and `postalCode` are request-required. `dateOfBirth`, when supplied, must be in the past; although its DTO validation permits null, the profile is not considered complete and account opening remains ineligible until it is supplied. `addressLine2` is optional. Field limits: name fields 120, address lines 160, city/state/country 80, postal code 20.

Phone and email are not accepted here; verified contact identity is owned by APP_USER.

### 8.3 Submit or replace own KYC

`PUT /api/customers/me/kyc` — Authenticated — success `200`

```json
{
  "aadhaarNumber": "123456789012",
  "panNumber": "ABCDE1234F"
}
```

- Aadhaar: exactly 12 digits.
- PAN: five letters, four digits, one letter; input is case-insensitive by regex.
- Raw Aadhaar and PAN are encrypted at rest and are never returned.

`data` fields: `kycId`, `userId`, `maskedAadhaar`, `maskedPan`, `status`, `rejectionReason`, `verifiedAt`, `createdAt`, `updatedAt`.

New/re-submitted KYC is reviewed through status values `PENDING`, `VERIFIED`, or `REJECTED`.

### 8.4 Get own KYC

`GET /api/customers/me/kyc` — Authenticated — success `200`

Returns the masked KYC response above. `404` means KYC has not been submitted.

### 8.5 Get customer by user ID

`GET /api/customers/{id}` — ADMIN — success `200`

`id` is `customerId`, not the APP_USER/user ID. Returns the profile response. Admin list/search results contain both identifiers; use the `customerId` field for this path.

### 8.6 Update KYC status

`PUT /api/customers/{userId}/kyc/status` — ADMIN — success `200`

```json
{
  "status": "VERIFIED",
  "rejectionReason": null
}
```

The DTO enum contains `PENDING`, `VERIFIED`, and `REJECTED`, but this administrative transition endpoint currently accepts only `VERIFIED` or `REJECTED`. A nonblank `rejectionReason` is mandatory for `REJECTED` and may contain at most 240 characters. Sending `PENDING` returns `400`.

Verification additionally requires current `AADHAAR` and `PAN` document records. `ADDRESS_PROOF` is optional. Attempting to verify without both required documents returns `400`.

### 8.7 Upload or replace a KYC document

`POST /api/customers/me/kyc/documents` - CUSTOMER - success `201`

This is a `multipart/form-data` request, not JSON. The KYC identity details from section 8.3 must exist first.

| Multipart field | Part type | Required | Value |
|---|---|---:|---|
| `documentType` | Text | Yes | `AADHAAR`, `PAN`, or `ADDRESS_PROOF` |
| `file` | File | Yes | PDF, JPG/JPEG, or PNG |

Do not manually set the request-level `Content-Type` header. `FormData` or the HTTP client must generate the multipart boundary.

Browser example:

```javascript
const form = new FormData();
form.append("documentType", "AADHAAR");
form.append("file", selectedFile);

const response = await fetch(`${API_BASE_URL}/api/customers/me/kyc/documents`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`
  },
  body: form
});
```

Constraints:

- Maximum physical file size is currently 5 MB and is environment-configurable.
- The filename extension, supplied media type, and basic file signature must agree.
- Empty files and unsupported content return `400`.
- A request rejected by the servlet-level multipart limit returns `413`.
- One current document is allowed per customer and document type.
- Uploading an existing type replaces its physical file and metadata while retaining its `documentId` and protected URL.
- Upload/replace is blocked after KYC reaches `VERIFIED`.
- Replacing a document while KYC is `REJECTED` automatically moves the overall KYC back to `PENDING` and clears `rejectionReason`.

Response `data`:

```json
{
  "documentId": "94d426a1-0d85-4fc5-975c-62974c03c738",
  "userId": "22181253-86b4-4f2b-9844-852bdba978ed",
  "documentType": "AADHAAR",
  "originalFileName": "aadhaar.pdf",
  "contentType": "application/pdf",
  "fileSize": 284312,
  "documentUrl": "http://localhost:8080/api/customers/me/kyc/documents/94d426a1-0d85-4fc5-975c-62974c03c738/content",
  "uploadedAt": "2026-08-06T10:30:00Z",
  "updatedAt": "2026-08-06T10:30:00Z"
}
```

The frontend must treat `documentId`, `userId`, and `documentUrl` as opaque server values. Physical volume paths and generated stored filenames are intentionally not returned.

### 8.8 List the current customer's KYC documents

`GET /api/customers/me/kyc/documents` - CUSTOMER - success `200`

Returns `data[]` containing the document metadata described in section 8.7. The backend derives ownership from JWT `sub`; there is no customer-supplied `userId` query parameter.

Use this response to determine whether the customer has uploaded required types. The frontend may show "ready for review" when both `AADHAAR` and `PAN` are present, but the backend remains authoritative.

### 8.9 Display or download a customer KYC document

`GET /api/customers/me/kyc/documents/{documentId}/content` - document owner - success `200` binary

This protected URL returns the actual PDF/image with its stored media type and an inline `Content-Disposition` filename. It is not a public static URL and always requires the customer's JWT.

A normal `<img src="...">`, `<iframe src="...">`, or direct browser navigation cannot attach a bearer header. Fetch the file as a Blob first:

```javascript
const response = await fetch(document.documentUrl, {
  headers: {
    Authorization: `Bearer ${token}`
  }
});

if (!response.ok) {
  throw await toApiError(response);
}

const blob = await response.blob();
const objectUrl = URL.createObjectURL(blob);

// Use objectUrl as an image src, PDF viewer URL, or download link.
// Later, when the preview closes or the component unmounts:
// URL.revokeObjectURL(objectUrl);
```

The frontend must not assume that knowing a `documentId` grants access. A document that is absent or owned by another customer returns ownership-safe `404`.

### 8.10 Delete a customer KYC document

`DELETE /api/customers/me/kyc/documents/{documentId}` - document owner - success `204`, no body

This removes both database metadata and the stored physical file. Deletion is allowed only while KYC is `PENDING` or `REJECTED`; it returns `400` after verification. Never attempt to parse a JSON response for the successful `204` case.

### 8.11 Administrator KYC review queue

`GET /api/customers/kyc/reviews?status={status}` - ADMIN - success `200`

`status` is optional:

| Variation | Purpose |
|---|---|
| Omit `status` | All KYC review summaries |
| `?status=PENDING` | Customers awaiting review or re-review |
| `?status=VERIFIED` | Approved customers |
| `?status=REJECTED` | Rejected customers awaiting correction |

An invalid enum value returns `400`. This endpoint returns metadata only; it deliberately does not load every sensitive document file.

Response item:

```json
{
  "kycId": "c74d979b-e7fc-48bf-a439-f77d31311b90",
  "userId": "22181253-86b4-4f2b-9844-852bdba978ed",
  "status": "PENDING",
  "rejectionReason": null,
  "documentCount": 2,
  "uploadedDocumentTypes": ["AADHAAR", "PAN"],
  "createdAt": "2026-08-06T10:00:00Z",
  "updatedAt": "2026-08-06T10:30:00Z"
}
```

Use the returned APP_USER `userId`, not `kycId` or `customerId`, in the admin document routes below.

### 8.12 Administrator lists one customer's documents

`GET /api/customers/{userId}/kyc/documents` - ADMIN - success `200`

Every admin document request deliberately includes the customer `userId`; there is no broad endpoint that returns all customers' document files. The returned metadata uses an admin-specific protected URL:

```text
http://localhost:8080/api/customers/{userId}/kyc/documents/{documentId}/content
```

### 8.13 Administrator displays one customer's document

`GET /api/customers/{userId}/kyc/documents/{documentId}/content` - ADMIN - success `200` binary

The backend verifies both values as a pair. If `documentId` belongs to a different customer than `userId`, the response is `404`. Fetch and display it as a Blob using the same approach as section 8.9, but with the admin JWT.

### 8.14 Required frontend KYC lifecycle

The frontend should call the APIs in this order:

1. Customer logs in and obtains a customer JWT.
2. Customer completes `PUT /api/customers/me` when the profile is incomplete.
3. Customer submits identity values with `PUT /api/customers/me/kyc`; KYC becomes `PENDING`.
4. Customer uploads `AADHAAR` with `POST /api/customers/me/kyc/documents`.
5. Customer uploads `PAN` using the same endpoint. `ADDRESS_PROOF` may be uploaded optionally.
6. Customer calls `GET /api/customers/me/kyc/documents` to confirm the current documents and retain their protected URLs.
7. Admin calls `GET /api/customers/kyc/reviews?status=PENDING` and selects a `userId`.
8. Admin calls `GET /api/customers/{userId}/kyc/documents`.
9. Admin fetches each returned content URL with the admin bearer token.
10. Admin either verifies or rejects using `PUT /api/customers/{userId}/kyc/status`.

Approval path:

```text
PENDING -> admin reviews Aadhaar/PAN -> VERIFIED -> documents become read-only
```

Rejection and correction path:

```text
PENDING
  -> admin sends REJECTED with a reason
  -> customer reads GET /api/customers/me/kyc
  -> customer replaces the rejected document type
  -> backend automatically returns KYC to PENDING
  -> admin reviews again
  -> VERIFIED or REJECTED
```

Frontend state rules:

- Always render the server's KYC `status`; do not infer approval only from document count.
- Show `rejectionReason` when status is `REJECTED`.
- Allow document replacement for `PENDING` and `REJECTED`.
- Hide or disable upload/delete controls for `VERIFIED`, while still handling a backend `400` in case UI state is stale.
- Refresh both `GET /api/customers/me/kyc` and `GET /api/customers/me/kyc/documents` after upload/replacement.
- After an admin decision, refresh the selected review queue and customer summary.
- Customer document content URLs and admin document content URLs are role-specific; use the URL returned by the corresponding list API.

### 8.15 KYC document status and error handling

| Status | Frontend meaning |
|---:|---|
| `200` | Metadata/status returned or binary file streamed |
| `201` | Document uploaded or replaced |
| `204` | Document deleted; do not parse a body |
| `400` | Invalid document, invalid enum/lifecycle action, or required Aadhaar/PAN missing at verification |
| `401` | Missing, invalid, expired, or inactive-session JWT; return to login |
| `403` | Authenticated user lacks ADMIN permission |
| `404` | KYC/document missing or ownership/user-document pairing failed |
| `413` | Multipart request is larger than the configured request limit |
| `500` | Persistent file storage operation failed |

## 9. Branch APIs

All require authentication.

| Method and path | Purpose | Success |
|---|---|---:|
| `GET /api/branches` | List branch summaries | `200` |
| `GET /api/branches/ifsc/{ifsc}` | Resolve branch by IFSC | `200` |
| `GET /api/branches/{id}` | Resolve branch by branch ID | `200` |

IFSC format: `^[A-Z]{4}0[A-Z0-9]{6}$`, for example `ORCL0000123`.

Summary fields: `branchId`, `branchName`, `ifsc`, `city`. Detail adds `state`.

Routing note: `/ifsc/{ifsc}` is distinct from `/{id}`; always URL-encode path values.

## 10. Account APIs

Account types: `SAVINGS`, `CURRENT`, `SALARY`.

Account statuses: `ACTIVE`, `FROZEN`, `CLOSED`, `INACTIVE`.

### 10.1 List accounts

`GET /api/accounts` — Authenticated — success `200`

Variations:

- CUSTOMER: omit `customerUserId`; the server always returns the current user's accounts.
- ADMIN: omit `customerUserId` to list all accounts.
- ADMIN: provide `?customerUserId={userId}` to list one user's accounts.
- A CUSTOMER-provided `customerUserId` does not broaden access.

`data[]`: `accountId`, `accountNumber`, `accountType`, `branchIfsc`, `status`, `availableBalance`, `primaryAccount`.

### 10.2 Get account detail

`GET /api/accounts/{id}` — Owner or ADMIN — success `200`

Adds `customerUserId`, `ledgerBalance`, `createdAt`, and `updatedAt`.

### 10.3 Get balance

`GET /api/accounts/{id}/balance` — Owner or ADMIN — success `200`

`data`: `accountId`, `accountNumber`, `availableBalance`, `ledgerBalance`.

### 10.4 Mini statement

`GET /api/accounts/{id}/mini-statement?limit=10` — Owner or ADMIN — success `200`

`limit` defaults to 10 and must be 1-25.

`data`: `accountId`, `accountNumber`, `transactions[]`. Each transaction contains `transactionId`, `transactionType`, `referenceNumber`, `amount`, `debitCredit`, `status`, `transactionDate`.

### 10.5 Change account status

`PUT /api/accounts/{id}/status` — ADMIN — success `200`

```json
{ "status": "FROZEN" }
```

Returns full account detail. This is an administrative state change, not an account-closing workflow.

## 11. Beneficiary APIs

Relationships: `SELF`, `PARENT`, `SPOUSE`, `CHILD`, `SIBLING`, `RELATIVE`, `FRIEND`, `BUSINESS`, `OTHER`.

Statuses: `PENDING`, `VERIFIED`, `BLOCKED`.

### 11.1 List beneficiaries

`GET /api/beneficiaries?favouritesOnly=false` — Authenticated — success `200`

Always scoped to the current user. Set `favouritesOnly=true` for favourites only.

### 11.2 Get beneficiary

`GET /api/beneficiaries/{id}` — Owner or ADMIN — success `200`

### 11.3 Create beneficiary

`POST /api/beneficiaries` — Authenticated — success `201`

```json
{
  "nickname": "Rent",
  "beneficiaryName": "Landlord Name",
  "relationship": "OTHER",
  "accountNumber": "123456789012",
  "ifscCode": "ORCL0000123",
  "favourite": true
}
```

Validation: nickname max 80, name max 120, account number max 30, valid IFSC, relationship required.

The backend validates the destination account and IFSC. Use `SELF` only for an account owned by the current customer; an own account requires `SELF`.

### 11.4 Update beneficiary

`PUT /api/beneficiaries/{id}` — Owner — success `200`

Uses the same complete payload as create; this is replacement-style update, not PATCH.

### 11.5 Delete beneficiary

`DELETE /api/beneficiaries/{id}` — Owner — success `204`, no body.

### 11.6 Change beneficiary status

`PUT /api/beneficiaries/{id}/status` — ADMIN — success `200`

```json
{ "status": "VERIFIED" }
```

Response fields for detail/create/update/status: `beneficiaryId`, `customerUserId`, `nickname`, `beneficiaryName`, `relationship`, `accountNumber`, `ifscCode`, `status`, `favourite`, `createdAt`, `updatedAt`.

## 12. Transaction read APIs

Transaction types: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `BILL_PAYMENT`, `LOAN_REPAYMENT`.

Statuses: `SUCCESS`, `PENDING`, `FAILED`, `REVERSED`.

Debit/credit values in responses: `DEBIT`, `CREDIT`.

Transaction fields: `transactionId`, `accountId`, `accountNumber`, `customerUserId`, `transactionType`, `referenceNumber`, `referenceType`, `amount`, `status`, `debitCredit`, `description`, `transactionDate`.

### 12.1 List current scope

`GET /api/transactions?page=0&size=20` — Authenticated — success `200`

CUSTOMER sees only their transactions. ADMIN sees all. Returns standard `ApiResponse<Page<TransactionResponse>>`.

### 12.2 Get by ID

`GET /api/transactions/{id}` — Owner or ADMIN — success `200`

### 12.3 List by account

`GET /api/transactions/account/{accountId}?page=0&size=20` — Account owner or ADMIN — success `200`

### 12.4 Search transactions

`GET /api/transactions/search` — Authenticated — success `200`

Optional filters:

| Query parameter | Type/values |
|---|---|
| `accountId` | Account ID |
| `accountNumber` | Exact account number |
| `transactionType` | Transaction type enum |
| `status` | Transaction status enum |
| `minAmount` | Decimal |
| `maxAmount` | Decimal |
| `referenceNumber` | Exact reference |
| `fromDate` | ISO instant |
| `toDate` | ISO instant |
| `page` | Default `0` |
| `size` | Default `20` |
| `sortBy` | `transactionDate`, `amount`, `status`, `transactionType`, or `referenceNumber` |
| `direction` | `asc` or `desc`; default `desc` |

CUSTOMER ownership is always applied in addition to supplied filters. Example:

```text
/api/transactions/search?accountId=ACCOUNT_ID&transactionType=TRANSFER&status=SUCCESS&fromDate=2026-08-01T00%3A00%3A00Z&toDate=2026-08-31T23%3A59%3A59Z&page=0&size=20&sortBy=transactionDate&direction=desc
```

### 12.5 Statement data

`GET /api/transactions/statement?accountId={id}&fromDate={instant}&toDate={instant}` — Owner or ADMIN — success `200`

`accountId` is mandatory; dates are optional. Returns `accountId`, effective `fromDate`, effective `toDate`, and `transactions[]`. This is JSON statement data. Use Reports for downloadable CSV/PDF.

## 13. Banking Workflow APIs

These commands orchestrate multi-service operations and compensation. All require authentication and a mandatory `Idempotency-Key` header.

Common headers:

```http
Authorization: Bearer <jwt>
Content-Type: application/json
Idempotency-Key: <crypto.randomUUID()>
```

### 13.1 Open account

`POST /api/banking/accounts/open` — CUSTOMER/Authenticated — success `201`

```json
{
  "accountType": "SAVINGS",
  "branchIfsc": "ORCL0000123"
}
```

Prerequisites: completed customer profile, `VERIFIED` KYC, eligible onboarding status, and a valid branch IFSC. The server generates account ID and account number. The first account becomes primary.

`data`: `referenceNumber`, `accountId`, `accountNumber`, `accountType`, `branchIfsc`, `status`, `primaryAccount`.

Expected domain errors: `409` incomplete profile/KYC/ineligible or idempotency conflict; `503` downstream unavailable/compensation pending.

### 13.2 Transfer

`POST /api/banking/transfer` — Account owner or ADMIN — success `200`

```json
{
  "sourceAccountId": "ACCOUNT_ID",
  "destinationAccountNumber": "DESTINATION_ACCOUNT_NUMBER",
  "amount": 500.00,
  "description": "Monthly rent"
}
```

Rules: positive amount; source active; sufficient balance; source and destination must differ; destination must be an active, VERIFIED beneficiary for the customer.

`data`: `referenceNumber`, `sourceAccountId`, `destinationAccountId`, `amount`, `status`, `debitTransactionId`, `creditTransactionId`.

### 13.3 Bill payment

`POST /api/banking/bill-payments` — Authenticated — success `201`

```json
{
  "sourceAccountId": "ACCOUNT_ID",
  "customerBillerId": "CUSTOMER_BILLER_ID",
  "amount": 1250.00,
  "description": "August electricity bill"
}
```

Rules: the source account and registered biller belong to the authenticated customer; both are active; sufficient balance; amount positive.

`data`: `referenceNumber`, `billPaymentId`, `transactionId`, `sourceAccountId`, `amount`, `status`.

### 13.4 Repay loan

`POST /api/banking/loans/{loanId}/repay` — Authenticated — success `200`

```json
{
  "sourceAccountId": "ACCOUNT_ID",
  "amount": 10000.00,
  "description": "EMI payment"
}
```

Rules: loan and source account belong to the customer; account active; loan is `ACTIVE` or `OVERDUE`; amount does not exceed outstanding balance; sufficient account balance.

`data`: `referenceNumber`, `loanId`, `loanRepaymentId`, `transactionId`, `sourceAccountId`, `amount`, `status`.

### 13.5 Deposit

`POST /api/banking/deposit` — Account owner or ADMIN — success `200`

```json
{
  "accountId": "ACCOUNT_ID",
  "amount": 1000.00,
  "description": "Cash deposit"
}
```

`data`: `referenceNumber`, `accountId`, `amount`, `status`, `transactionId`.

### 13.6 Withdraw

`POST /api/banking/withdraw` — Account owner or ADMIN — success `200`

```json
{
  "accountId": "ACCOUNT_ID",
  "amount": 500.00,
  "description": "ATM withdrawal"
}
```

Requires an active account and sufficient balance. Response shape matches deposit.

### 13.7 UI state for saga outcomes

- Do not optimistically alter the authoritative balance before success.
- Disable duplicate submission while a request is in flight, but retain the idempotency key.
- On `200/201`, invalidate account balance, transaction, workflow-dependent, bill-payment, and loan caches as applicable.
- On `409`, show the server message and refresh affected resources.
- On `503` mentioning compensation, show a pending-reconciliation state and instruct the user not to resubmit with a new key.

## 14. Biller and bill-payment APIs

Biller categories: `ELECTRICITY`, `WATER`, `GAS`, `TELECOM`, `INTERNET`, `INSURANCE`, `OTHER`.

Catalog statuses: `ACTIVE`, `INACTIVE`. Customer registration statuses: `ACTIVE`, `INACTIVE`.

### 14.1 Biller catalog

| Method and path | Role | Body/query | Success |
|---|---|---|---:|
| `GET /api/billers/catalog` | Authenticated | Optional `category` | `200` |
| `GET /api/billers/catalog/{id}` | Authenticated | Active catalog biller ID | `200` |
| `POST /api/billers/catalog` | ADMIN | Catalog request | `201` |
| `PUT /api/billers/catalog/{id}` | ADMIN | Full catalog request | `200` |
| `DELETE /api/billers/catalog/{id}` | ADMIN | No body; deactivates | `204` |

Catalog request:

```json
{
  "billerCode": "TNEB",
  "billerName": "Tamil Nadu Electricity Board",
  "category": "ELECTRICITY",
  "status": "ACTIVE"
}
```

Limits: code 40, name 120. Response: `billerId`, `billerCode`, `billerName`, `category`, `status`, `createdAt`, `updatedAt`.

### 14.2 Customer-registered billers

| Method and path | Purpose | Success |
|---|---|---:|
| `GET /api/billers` | List current user's registrations | `200` |
| `POST /api/billers` | Register a catalog biller | `201` |
| `GET /api/billers/{id}` | Get current user's registration | `200` |
| `PUT /api/billers/{id}` | Replace registration details | `200` |
| `DELETE /api/billers/{id}` | Deactivate registration | `204` |

Create/update payload:

```json
{
  "billerId": "CATALOG_BILLER_ID",
  "consumerReference": "CONSUMER-12345",
  "nickname": "Home electricity"
}
```

`billerId` max 36; `consumerReference` and `nickname` max 80. The catalog biller must be active. Duplicate registration returns `409`.

Response: `customerBillerId`, `customerUserId`, nested `biller`, `consumerReference`, `nickname`, `status`, `createdAt`, `updatedAt`.

### 14.3 Bill-payment history

| Method and path | Purpose | Success |
|---|---|---:|
| `GET /api/bill-payments?page=0&size=20` | Unfiltered current-user history | `200` |
| `GET /api/bill-payments/history` | Filtered current-user history | `200` |
| `GET /api/bill-payments/{id}` | Owner or ADMIN detail | `200` |

History filters: `status`, `sourceAccountId`, `billerId`, `from`, `to`, `page`, `size`. Page must be >= 0 and size 1-100.

Bill-payment response: `billPaymentId`, `customerUserId`, `customerBillerId`, `billerId`, `billerName`, `consumerReference`, `sourceAccountId`, `amount`, `status`, `workflowReference`, `transactionId`, `transactionReference`, `description`, `failureReason`, `createdAt`, `updatedAt`, `completedAt`.

Payments are created through `POST /api/banking/bill-payments`, not through this read API.

## 15. Card APIs

Card types: `DEBIT`, `CREDIT`. Statuses: `INACTIVE`, `ACTIVE`, `BLOCKED`, `EXPIRED`.

Sensitive card numbers are never returned; responses contain `maskedCardNumber`.

### 15.1 Card products

`GET /api/cards/products` - Authenticated - success `200`

Use this endpoint for the card product dropdown. It returns debit and credit variants for each product tier.

Example response item:

```json
{
  "cardType": "CREDIT",
  "code": "GOLD",
  "label": "Gold Credit Card",
  "minimumAnnualIncome": 600000,
  "defaultDailyLimit": 25000,
  "defaultCreditLimit": 100000
}
```

For debit products, `defaultCreditLimit` is `null`.

### 15.2 Submit card application

`POST /api/cards/applications` - CUSTOMER - success `201`

The backend uses the logged-in JWT user. Do not send `customerUserId` from the frontend.

```json
{
  "accountId": "ACCOUNT_ID",
  "cardType": "CREDIT",
  "cardProduct": "GOLD",
  "annualIncome": 700000,
  "occupation": "Software Engineer",
  "deliveryAddress": "Home address",
  "requestedDailyLimit": 25000
}
```

`cardType` can be `DEBIT` or `CREDIT`. If omitted, backend treats it as `DEBIT` for backward compatibility.

Only one pending application or non-expired card can exist per account per card type. One debit card and one credit card may exist for the same account, but two active credit cards for the same account are rejected with `409`.

### 15.3 My card applications

- `GET /api/cards/applications` - CUSTOMER - success `200`
- `GET /api/cards/applications/{id}` - Owner or ADMIN - success `200`

Application fields include `applicationId`, `customerUserId`, `accountId`, `cardType`, `cardProduct`, `annualIncome`, `occupation`, `deliveryAddress`, `requestedDailyLimit`, `approvedDailyLimit`, `approvedCreditLimit`, `status`, `rejectionReason`, `decisionNotes`, `issuedCardId`, `decidedByUserId`, `createdAt`, `updatedAt`, and `decidedAt`.

### 15.4 Admin card application review

- `GET /api/cards/admin/applications?status=PENDING&page=0&size=50` - ADMIN - success `200`
- `POST /api/cards/admin/applications/{id}/approve` - ADMIN - success `200`
- `POST /api/cards/admin/applications/{id}/reject` - ADMIN - success `200`

Approve body:

```json
{
  "approvedDailyLimit": 25000,
  "notes": "Approved after review"
}
```

Reject body:

```json
{
  "reason": "Eligibility criteria not met"
}
```

Approval creates an `INACTIVE` card. Credit-card approval also creates a linked credit-card account with configured product credit limit.

Direct `POST /api/cards` is retired from the frontend contract. The customer-facing path is application submission plus admin approval.

### 15.5 List cards

`GET /api/cards` — Authenticated — success `200`

- CUSTOMER: own cards.
- ADMIN without filter: all cards.
- ADMIN with `?customerUserId={id}`: cards for that user.

### 15.6 Get card/detail status

- `GET /api/cards/{id}` — Owner or ADMIN — `200`
- `GET /api/cards/{id}/status` — Owner or ADMIN — `200`

Detail fields: `cardId`, `customerUserId`, `accountId`, `maskedCardNumber`, `cardType`, `status`, `dailyTransactionLimit`, `expiryMonth`, `expiryYear`, `blockedReason`, `createdAt`, `updatedAt`, `activatedAt`, `blockedAt`.

Status response is the smaller subset: `cardId`, `maskedCardNumber`, `status`, `dailyTransactionLimit`, `expiryMonth`, `expiryYear`.

### 15.7 Legacy direct issue route

`POST /api/cards` — ADMIN — success `201`

This route is retained only as a backend/admin compatibility route. Frontend teams should not use it for new card issuance. Use `POST /api/cards/applications` and admin approval instead.

```json
{
  "customerUserId": "USER_ID",
  "accountId": "ACCOUNT_ID",
  "cardType": "DEBIT",
  "dailyTransactionLimit": 50000.00
}
```

The account must be active and owned by `customerUserId`; a non-expired card must not already exist for it.

### 15.8 Credit-card accounts

- `GET /api/cards/credit-accounts` - CUSTOMER/ADMIN - success `200`
- `GET /api/cards/{id}/credit-account` - Owner or ADMIN - success `200`

ADMIN may call `GET /api/cards/credit-accounts?customerUserId={id}`.

Credit-account fields include `creditAccountId`, `cardId`, `customerUserId`, `accountId`, `cardProduct`, `creditLimit`, `availableCredit`, `outstandingBalance`, `billingCycleDay`, `status`, `createdAt`, and `updatedAt`.

### 15.9 Activate card

`POST /api/cards/{id}/activate` — Owner — success `200`; no request body. Returns the updated card response.

Only an `INACTIVE` card can be activated.

### 15.10 Block card

`POST /api/cards/{id}/block` — Owner or ADMIN — success `200`

Body is optional. Both calls are valid:

```json
{}
```

```json
{ "reason": "Card lost" }
```

Reason maximum: 240 characters. The card must be in a blockable state.

### 15.11 Unblock card

`POST /api/cards/{id}/unblock` — Owner or ADMIN — success `200`; no request body. Returns the updated card response.

Only a `BLOCKED` card can be unblocked.

### 15.12 Change daily limit

`PUT /api/cards/{id}/limit` — Owner — success `200`

```json
{ "dailyTransactionLimit": 25000.00 }
```

Must be positive and not exceed the server-configured maximum. An expired card cannot be changed.

## 16. Loan APIs

Loan types: `HOME`, `VEHICLE`, `PERSONAL`, `EDUCATION`, `BUSINESS`.

Loan statuses: `ACTIVE`, `CLOSED`, `OVERDUE`, `DEFAULTED`.

EMI statuses: `PENDING`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`.

Repayment statuses: `PENDING`, `SUCCESS`, `CANCELLED`, `FAILED`, `REVERSED`.

### 16.1 Backend/admin direct loan registration

`POST /api/loans` — ADMIN — success `201`

```json
{
  "customerUserId": "USER_ID",
  "linkedAccountId": "ACCOUNT_ID",
  "loanType": "HOME",
  "principalAmount": 2500000.00,
  "annualInterestRate": 8.5,
  "tenureMonths": 240,
  "startDate": "2026-08-05"
}
```

Principal > 0; interest >= 0; tenure 1-360; start date is optional. The server generates the loan number, EMI amount, maturity date, and EMI schedule.

This route is retained for backend/admin compatibility. Frontend customer flows should use `POST /api/loans/applications` and admin approval instead of direct loan registration.

### 16.2 Loan-type options

`GET /api/loans/types` - Authenticated - success `200`

Returns `[{ "code": "HOME", "label": "Home" }, ...]`. Prefer this endpoint for dropdown options instead of hardcoding display labels.

### 16.3 Submit loan application

`POST /api/loans/applications` - CUSTOMER - success `201`

The backend uses the logged-in JWT user. Do not send `customerUserId` from the frontend.

```json
{
  "linkedAccountId": "ACCOUNT_ID",
  "loanType": "HOME",
  "requestedAmount": 100000,
  "tenureMonths": 12,
  "monthlyIncome": 50000,
  "employmentType": "SALARIED",
  "purpose": "Home renovation"
}
```

### 16.4 My loan applications

- `GET /api/loans/applications` - CUSTOMER - success `200`
- `GET /api/loans/applications/{id}` - Owner or ADMIN - success `200`

Application fields include `applicationId`, `customerUserId`, `linkedAccountId`, `loanType`, `requestedAmount`, `requestedTenureMonths`, `monthlyIncome`, `employmentType`, `purpose`, `approvedAmount`, `approvedAnnualInterestRate`, `approvedTenureMonths`, `status`, `rejectionReason`, `decisionNotes`, `issuedLoanId`, `decidedByUserId`, `createdAt`, `updatedAt`, and `decidedAt`.

### 16.5 Admin loan application review

- `GET /api/loans/admin/applications?status=PENDING&page=0&size=50` - ADMIN - success `200`
- `POST /api/loans/admin/applications/{id}/approve` - ADMIN - success `200`
- `POST /api/loans/admin/applications/{id}/reject` - ADMIN - success `200`

Approve body:

```json
{
  "approvedAmount": 100000,
  "approvedAnnualInterestRate": 10.5,
  "approvedTenureMonths": 12,
  "notes": "Approved after review"
}
```

Reject body:

```json
{
  "reason": "Eligibility criteria not met"
}
```

Approval issues the loan and generates the EMI schedule.

### 16.6 List loans

`GET /api/loans` — Authenticated — success `200`

Optional: `customerUserId`, `status`.

- CUSTOMER always sees own loans and may filter own loans by status.
- ADMIN may omit `customerUserId` for all loans or supply it for one user.

Summary fields: `loanId`, `customerUserId`, `linkedAccountId`, `loanNumber`, `loanType`, `principalAmount`, `emiAmount`, `outstandingBalance`, `status`, `startDate`, `maturityDate`.

### 16.7 Loan details and balance

- `GET /api/loans/{id}` — Owner or ADMIN — `200`
- `GET /api/loans/{id}/balance` — Owner or ADMIN — `200`

Detail adds interest rate, tenure, created/updated/closed timestamps. Balance returns `loanId`, `loanNumber`, `loanType`, `outstandingBalance`, `emiAmount`, `status`.

### 16.8 EMI schedule

`GET /api/loans/{id}/schedule` — Owner or ADMIN — success `200`

Each item: `emiScheduleId`, `installmentNumber`, `dueDate`, `openingBalance`, `principalDue`, `interestDue`, `totalDue`, `amountPaid`, `status`, `paidAt`, `reminderSentAt`, `overdueNotifiedAt`.

### 16.9 Repayment history

`GET /api/loans/{id}/history` — Owner or ADMIN — success `200`

Each item: `loanRepaymentId`, `loanId`, `customerUserId`, `sourceAccountId`, `amount`, `workflowReference`, `transactionId`, `transactionReference`, `status`, `failureReason`, `principalApplied`, `createdAt`, `updatedAt`, `completedAt`, `reversedAt`.

Make repayments through the Banking Workflow endpoint, not this route.

### 16.10 Update loan status

`PUT /api/loans/{id}/status` — ADMIN — success `200`

```json
{ "status": "OVERDUE" }
```

Closed loans cannot be reopened, and manual transition to `CLOSED` is rejected because closure occurs automatically when outstanding balance reaches zero.

### 16.11 EMI calculator

`POST /api/loans/calculate` — Authenticated — success `200`

```json
{
  "loanAmount": 1000000.00,
  "annualInterestRate": 8.5,
  "tenureMonths": 120,
  "startDate": "2026-08-05"
}
```

Returns `monthlyEmi`, `totalInterest`, `totalRepayment`, and `schedulePreview[]` with installment, due date, opening balance, principal, interest, total due, and closing balance. This calculation does not create a loan.

## 17. Scheduled bill-payment APIs

User-created schedules currently represent bill payments. Operation type in their response is `BILL_PAYMENT`; system-owned scheduler jobs may use `EMI_REMINDER_SCAN` or `LOAN_OVERDUE_SCAN` but cannot be changed through customer endpoints.

Schedule types: `ONE_TIME`, `DAILY`, `WEEKLY`, `MONTHLY`.

Schedule statuses: `ACTIVE`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`.

Execution statuses: `PENDING`, `RUNNING`, `RETRY_WAIT`, `SUCCEEDED`, `FAILED`.

### 17.1 Create schedule

`POST /api/schedules` — Authenticated — success `201`

```json
{
  "scheduleType": "MONTHLY",
  "sourceAccountId": "ACCOUNT_ID",
  "customerBillerId": "CUSTOMER_BILLER_ID",
  "amount": 1500.00,
  "description": "Monthly internet bill",
  "timezone": "Asia/Kolkata",
  "startAt": "2026-09-01T04:30:00Z",
  "endAt": "2027-09-01T04:30:00Z",
  "maxRetries": 3
}
```

Rules: amount > 0; `endAt`, when present, must be after `startAt`; timezone must be a valid IANA zone; `maxRetries` optional, 0-10, with a server default when omitted.

### 17.2 List schedules

`GET /api/schedules` — Authenticated — success `200`

Optional filters: `customerUserId`, `status`.

- CUSTOMER sees own schedules regardless of supplied user ID.
- ADMIN may list all or filter by customer/status.

### 17.3 Details and executions

- `GET /api/schedules/{id}` — Owner or ADMIN — `200`
- `GET /api/schedules/{id}/executions` — Owner or ADMIN — `200`

Schedule fields: `scheduleId`, `customerUserId`, `operationType`, `scheduleType`, `sourceAccountId`, `customerBillerId`, `amount`, `description`, `timezone`, `startAt`, `nextExecutionAt`, `endAt`, `maxRetries`, `systemOwned`, `status`, `createdAt`, `updatedAt`.

Execution fields: `executionId`, `scheduleId`, `scheduledFor`, `attemptCount`, `status`, `workflowIdempotencyKey`, `workflowReference`, `responseSummary`, `failureReason`, `startedAt`, `completedAt`, `nextRetryAt`.

### 17.4 Update schedule

`PUT /api/schedules/{id}` — Owner — success `200`

Uses the full create payload. Terminal schedules cannot be changed. System-owned schedules are protected.

### 17.5 Pause and resume

- `POST /api/schedules/{id}/pause` — Owner — `200`; only `ACTIVE` may pause.
- `POST /api/schedules/{id}/resume` — Owner — `200`; only `PAUSED` may resume.

No request bodies.

### 17.6 Cancel

`DELETE /api/schedules/{id}` — Owner — success `204`, no body.

## 18. Email notification APIs

All routes require authentication. These are operational/testing endpoints; automatic customer emails are normally triggered by Kafka domain events. A production customer UI should not expose arbitrary recipient/template sending unless product authorization is added.

Notification statuses: `PENDING`, `PROCESSING`, `SENT`, `FAILED`, `RETRYING`.

### 18.1 Send using a template

`POST /api/notifications/email/send` — Authenticated — success `200`

```json
{
  "recipient": "recipient@example.com",
  "templateName": "GENERIC_NOTIFICATION",
  "variables": {
    "message": "Hello from Internet Banking"
  },
  "sourceEvent": "manual-ui",
  "referenceId": "optional-reference"
}
```

Recipient must be valid email; template name required, max 80; variables/source/reference optional. The template must exist and be active.

### 18.2 Direct SMTP test

`POST /api/notifications/email/test` — Authenticated — success `200`

```json
{
  "recipient": "configured-sender@example.com",
  "variables": { "message": "SMTP test" }
}
```

Uses `GENERIC_NOTIFICATION` and source event `manual-test`.

### 18.3 Kafka-to-email test

`POST /api/notifications/email/test-kafka` — Authenticated — success `200`

Uses the same test payload, publishes an event, and returns an event identifier/string under `data`. Email delivery occurs asynchronously; use history/status to observe it.

### 18.4 Get and list notifications

- `GET /api/notifications/email/{id}` — `200`
- `GET /api/notifications/email/history` — `200`
- `GET /api/notifications/email/failed` — `200`
- `GET /api/notifications/email/pending` — `200`

Detail fields: `notificationId`, `recipient`, `subject`, `status`, `retryCount`, `createdAt`, `sentAt`.

Summary/history adds `type`.

Routing warning: call literal `/history`, `/failed`, and `/pending` as shown; do not treat those words as notification IDs in client routing.

### 18.5 Retry failed notification

`POST /api/notifications/email/{id}/retry` — Authenticated — success `200`; no request body. Returns the updated email notification response.

Use only for retryable failed records. Do not implement an unbounded frontend retry loop.

## 19. Audit APIs

All Audit APIs are ADMIN-only and return raw DTOs, not `ApiResponse`.

Audit item fields: `auditId`, `eventId`, `eventVersion`, `eventType`, `occurredAt`, `ingestedAt`, `actorUserId`, `actorRole`, `sourceService`, `action`, `entityType`, `referenceId`, `correlationId`, `status`, `severity`, `sanitizedMetadata`.

`sanitizedMetadata` is a JSON string, not an already-parsed object:

```ts
const metadata = item.sanitizedMetadata
  ? JSON.parse(item.sanitizedMetadata)
  : {};
```

### 19.1 Search

`GET /api/audit` — ADMIN — success `200`

Optional filters: `from`, `to`, `actorUserId`, `action`, `sourceService`, `entityType`, `referenceId`, `correlationId`, `status`, `severity`.

Pagination: `page` default 0; `size` default 50, range 1-100.

Sorting: `sort` defaults to `occurredAt`; `direction` defaults to `desc`. Supported sort fields are controlled by Audit Service; use the default unless the UI exposes a tested sort option.

### 19.2 Detail

`GET /api/audit/{id}` — ADMIN — success `200`

`id` is `auditId`, not producer `eventId`.

### 19.3 User timeline

`GET /api/audit/users/{userId}?from={instant}&to={instant}&page=0&size=50` — ADMIN — success `200`

Returns newest-first audit items for `actorUserId`.

### 19.4 Chronological timeline

`GET /api/audit/timeline` — ADMIN — success `200`

Accepts the same filters as search, plus page/size, but always orders `occurredAt` ascending. Use this for incident sequence views; use the main search for newest-first tables.

### 19.5 Summary

`GET /api/audit/summary?from={instant}&to={instant}` — ADMIN — success `200`

Raw response:

```json
{
  "from": "2026-08-01T00:00:00Z",
  "to": "2026-08-05T23:59:59Z",
  "total": 42,
  "byEventType": { "login-alert": 10 },
  "byStatus": { "SUCCESS": 40, "FAILED": 2 },
  "bySeverity": { "INFO": 40, "WARN": 2 }
}
```

## 20. Report APIs

Report endpoints return raw DTOs and implement an asynchronous job lifecycle.

Formats: `CSV`, `PDF`.

Job states: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `EXPIRED`.

### 20.1 Queue endpoints

Every queue call returns `202 Accepted`:

| Endpoint | Report type | Role |
|---|---|---|
| `POST /api/reports/account-statements` | `ACCOUNT_STATEMENT` | Authenticated; owner-scoped |
| `POST /api/reports/transactions` | `TRANSACTIONS` | Authenticated; owner-scoped for CUSTOMER |
| `POST /api/reports/customers` | `CUSTOMERS` | ADMIN |
| `POST /api/reports/cards` | `CARDS` | Authenticated; owner-scoped for CUSTOMER |
| `POST /api/reports/loans` | `LOANS` | Authenticated; owner-scoped for CUSTOMER |
| `POST /api/reports/bill-payments` | `BILL_PAYMENTS` | Authenticated; owner-scoped for CUSTOMER |
| `POST /api/reports/schedules` | `SCHEDULES` | Authenticated; owner-scoped for CUSTOMER |
| `POST /api/reports/admin-overview` | `ADMIN_OVERVIEW` | ADMIN |
| `POST /api/reports/audit` | `AUDIT` | ADMIN |

Common request:

```json
{
  "format": "PDF",
  "ownerUserId": null,
  "accountId": null,
  "from": "2026-08-01T00:00:00Z",
  "to": "2026-08-31T23:59:59Z",
  "filters": {}
}
```

Rules:

- `format` is mandatory.
- `accountId` is mandatory for account statements.
- If both dates exist, `from <= to` and the range must not exceed the server-configured maximum.
- CUSTOMER requests are forced to the authenticated user's owner scope; a supplied `ownerUserId` cannot broaden scope.
- ADMIN may use `ownerUserId` to scope a report to one user or omit it for broader report types.
- The `filters` map is forwarded only where the selected report's internal data source understands the keys.

Useful report filters:

| Report | Supported filter keys |
|---|---|
| Account statement | `accountId`, `from`, `to` |
| Transactions | `accountId`, `transactionType`, `status`, `from`, `to` |
| Customers | `status` |
| Cards | `cardType`, `status` |
| Loans | `loanType`, `status` |
| Bill payments | `status` |
| Schedules | `operationType`, `status`, `systemOwned`, `scheduleId`; output also includes matching executions |
| Audit | `actorUserId`, `action`, `sourceService`, `entityType`, `referenceId`, `correlationId`, `status`, `severity`, `from`, `to` |
| Admin overview | No row filter; returns summary sections |

Example account statement:

```http
POST /api/reports/account-statements
Authorization: Bearer <jwt>
Content-Type: application/json
Idempotency-Key: 7b62c35b-84e1-4b5b-9be6-31e7385565b9
```

```json
{
  "format": "CSV",
  "accountId": "ACCOUNT_ID",
  "from": "2026-08-01T00:00:00Z",
  "to": "2026-08-31T23:59:59Z",
  "filters": {}
}
```

Queue response:

```json
{
  "reportJobId": "REPORT_JOB_ID",
  "status": "QUEUED",
  "idempotentReplay": false
}
```

### 20.2 Poll job

`GET /api/reports/{id}` — Job owner or ADMIN — success `200`

```json
{
  "reportJobId": "REPORT_JOB_ID",
  "requesterUserId": "USER_ID",
  "requesterRole": "CUSTOMER",
  "reportType": "ACCOUNT_STATEMENT",
  "format": "CSV",
  "status": "COMPLETED",
  "failureReason": null,
  "createdAt": "2026-08-05T08:30:00Z",
  "startedAt": "2026-08-05T08:30:01Z",
  "completedAt": "2026-08-05T08:30:02Z",
  "generatedFile": {
    "fileName": "account-statement.csv",
    "contentType": "text/csv",
    "fileSize": 1024,
    "checksum": "SHA256_HEX",
    "rowCount": 10,
    "generatedAt": "2026-08-05T08:30:02Z",
    "expiresAt": "2026-08-12T08:30:02Z"
  }
}
```

Polling guidance: begin around 1-2 seconds, use capped backoff, stop at `COMPLETED`, `FAILED`, or `EXPIRED`, and stop when the component unmounts. Do not poll multiple times concurrently for the same job.

### 20.3 History

`GET /api/reports/history?page=0&size=20` — Authenticated — success `200`

Size range 1-100. CUSTOMER sees only own jobs and cannot use `requesterUserId` to view others. ADMIN sees all jobs when the filter is absent or may use `?requesterUserId={id}`.

### 20.4 Download

`GET /api/reports/{id}/download` — Job owner or ADMIN — success `200`

Only available after generation. It returns binary content with:

- `Content-Type: text/csv` or `application/pdf`;
- `Content-Length`;
- `Content-Disposition: attachment; filename=...`.

`404` means the generated file is not yet available or does not exist. `410` means it expired.

```ts
export async function downloadReport(id: string, token: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/reports/${encodeURIComponent(id)}/download`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const body = response.headers.get("content-type")?.includes("json")
      ? await response.json()
      : null;
    throw new ApiError(response.status, body);
  }
  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") ?? "";
  const filename = disposition.match(/filename\*?=(?:UTF-8''|\")?([^\";]+)/i)?.[1]
    ? decodeURIComponent(RegExp.$1.replace(/\"$/, ""))
    : `report-${id}`;
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
```

## 21. Admin APIs

All routes are ADMIN-only and return raw DTOs. Admin Service calls downstream internal read-only operation APIs; the frontend still calls only `/api/admin/**` through the Gateway.

### 21.1 Dashboard

`GET /api/admin/dashboard` — ADMIN — normally `200`

Returns:

```json
{
  "generatedAt": "2026-08-05T08:30:00Z",
  "sections": {
    "users": {
      "status": "AVAILABLE",
      "asOf": "2026-08-05T08:30:00Z",
      "data": { "total": 10, "active": 9, "inactive": 1 },
      "error": null
    }
  }
}
```

Section states: `AVAILABLE`, `DEGRADED`, `UNAVAILABLE`. Current implementation normally emits AVAILABLE or UNAVAILABLE. One unavailable downstream section does not fail the whole dashboard. The endpoint returns `503` only when all three core sections—users, accounts, and transactions—are unavailable.

Known section keys: `users`, `customers`, `branches`, `accounts`, `beneficiaries`, `transactions`, `workflows`, `bill-payments`, `cards`, `loans`, `schedules`, `audit`.

### 21.2 Resource tables

All return compact `{ items, page, size, totalElements, totalPages }` DTOs.

| Endpoint | Optional filters | Item highlights |
|---|---|---|
| `GET /api/admin/customers` | `status`, `page`, `size` | Profile/KYC state and location |
| `GET /api/admin/accounts` | `customerUserId`, `status`, `page`, `size` | Masked number, balances, status |
| `GET /api/admin/beneficiaries` | `customerUserId`, `status`, `page`, `size` | Masked number, relationship/status |
| `GET /api/admin/transactions` | `customerUserId`, `accountId`, `transactionType`, `status`, `fromDate`, `toDate`, `page`, `size` | Masked account and movement fields |
| `GET /api/admin/workflows` | `customerUserId`, `workflowType`, `status`, `page`, `size` | Saga type/status/failure |
| `GET /api/admin/loans` | `customerUserId`, `loanType`, `status`, `page`, `size` | Masked loan number/balances |
| `GET /api/admin/cards` | `customerUserId`, `cardType`, `status`, `page`, `size` | Masked card number/limit/status |
| `GET /api/admin/branches` | `page`, `size` | Branch/IFSC/location |
| `GET /api/admin/bill-payments` | `customerUserId`, `status`, `page`, `size` | Biller/payment/workflow status |
| `GET /api/admin/schedules` | `customerUserId`, `operationType`, `status`, `systemOwned`, `page`, `size` | Schedule timing/ownership/status |

The Admin proxy allow-lists query parameters and silently ignores unsupported names. It defaults size to 50 and caps it at the configured Admin maximum. Use only parameters listed above.

Users are represented in the dashboard/global search but there is currently no separate `GET /api/admin/users` public route. Customer profile detail can be fetched with `GET /api/customers/{customerId}`; do not substitute `userId` there.

### 21.3 Audit summary section

`GET /api/admin/audit-summary` — ADMIN — success `200`

Returns one `Section` wrapper whose `data` is the Audit summary. This is useful for dashboard cards; use `/api/audit/**` for detailed audit views.

### 21.4 System health

`GET /api/admin/system` — ADMIN — success `200`

Returns `generatedAt` and `services`, where each service is a `Section` based on its health endpoint. Render per-service status rather than converting one unavailable dependency into a blank page.

### 21.5 Global search

`GET /api/admin/search?query={text}` — ADMIN — success `200`

Query must contain 1-64 characters. Searches bounded result sets from `users`, `customers`, `accounts`, `beneficiaries`, `branches`, `loans`, and optionally `cards`.

Card search runs only when the query is 1-4 digits, matching the visible card suffix privacy model.

Response:

```json
{
  "generatedAt": "2026-08-05T08:30:00Z",
  "query": "gokul",
  "groups": {
    "users": [],
    "customers": [],
    "accounts": []
  }
}
```

Global search is bounded and intended for navigation/discovery, not exhaustive export. Use a resource table or Report API for complete data.

## 22. Complete endpoint index

This index is a final coverage checklist.

### Authentication and identity

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/auth/logout-all`
- `GET /api/auth/me`
- `POST /api/2fa/setup`
- `POST /api/2fa/verify-setup`
- `POST /api/2fa/verify`
- `POST /api/2fa/disable`
- `GET /api/2fa/status`

### Customer and branch

- `GET /api/customers/me`
- `PUT /api/customers/me`
- `PUT /api/customers/me/kyc`
- `GET /api/customers/me/kyc`
- `POST /api/customers/me/kyc/documents`
- `GET /api/customers/me/kyc/documents`
- `GET /api/customers/me/kyc/documents/{documentId}/content`
- `DELETE /api/customers/me/kyc/documents/{documentId}`
- `GET /api/customers/kyc/reviews`
- `GET /api/customers/{userId}/kyc/documents`
- `GET /api/customers/{userId}/kyc/documents/{documentId}/content`
- `GET /api/customers/{id}`
- `PUT /api/customers/{userId}/kyc/status`
- `GET /api/branches`
- `GET /api/branches/ifsc/{ifsc}`
- `GET /api/branches/{id}`

### Accounts, beneficiaries, and transactions

- `GET /api/accounts`
- `GET /api/accounts/{id}`
- `GET /api/accounts/{id}/balance`
- `GET /api/accounts/{id}/mini-statement`
- `PUT /api/accounts/{id}/status`
- `GET /api/beneficiaries`
- `GET /api/beneficiaries/{id}`
- `POST /api/beneficiaries`
- `PUT /api/beneficiaries/{id}`
- `DELETE /api/beneficiaries/{id}`
- `PUT /api/beneficiaries/{id}/status`
- `GET /api/transactions`
- `GET /api/transactions/{id}`
- `GET /api/transactions/account/{accountId}`
- `GET /api/transactions/search`
- `GET /api/transactions/statement`

### Orchestrated banking

- `POST /api/banking/accounts/open`
- `POST /api/banking/transfer`
- `POST /api/banking/bill-payments`
- `POST /api/banking/loans/{loanId}/repay`
- `POST /api/banking/deposit`
- `POST /api/banking/withdraw`

### Billers and bill payments

- `GET /api/billers/catalog`
- `GET /api/billers/catalog/{id}`
- `POST /api/billers/catalog`
- `PUT /api/billers/catalog/{id}`
- `DELETE /api/billers/catalog/{id}`
- `GET /api/billers`
- `POST /api/billers`
- `GET /api/billers/{id}`
- `PUT /api/billers/{id}`
- `DELETE /api/billers/{id}`
- `GET /api/bill-payments`
- `GET /api/bill-payments/history`
- `GET /api/bill-payments/{id}`

### Cards, loans, and schedules

- `GET /api/cards`
- `GET /api/cards/products`
- `POST /api/cards/applications`
- `GET /api/cards/applications`
- `GET /api/cards/applications/{id}`
- `GET /api/cards/admin/applications`
- `POST /api/cards/admin/applications/{id}/approve`
- `POST /api/cards/admin/applications/{id}/reject`
- `GET /api/cards/{id}`
- `GET /api/cards/{id}/status`
- `GET /api/cards/credit-accounts`
- `GET /api/cards/{id}/credit-account`
- `POST /api/cards/{id}/activate`
- `POST /api/cards/{id}/block`
- `POST /api/cards/{id}/unblock`
- `PUT /api/cards/{id}/limit`
- `GET /api/loans`
- `GET /api/loans/types`
- `POST /api/loans/applications`
- `GET /api/loans/applications`
- `GET /api/loans/applications/{id}`
- `GET /api/loans/admin/applications`
- `POST /api/loans/admin/applications/{id}/approve`
- `POST /api/loans/admin/applications/{id}/reject`
- `GET /api/loans/{id}`
- `GET /api/loans/{id}/balance`
- `GET /api/loans/{id}/schedule`
- `GET /api/loans/{id}/history`
- `PUT /api/loans/{id}/status`
- `POST /api/loans/calculate`
- `POST /api/schedules`
- `GET /api/schedules`
- `GET /api/schedules/{id}`
- `GET /api/schedules/{id}/executions`
- `PUT /api/schedules/{id}`
- `POST /api/schedules/{id}/pause`
- `POST /api/schedules/{id}/resume`
- `DELETE /api/schedules/{id}`

### Notifications, audit, reports, and admin

- `POST /api/notifications/email/send`
- `POST /api/notifications/email/test`
- `POST /api/notifications/email/test-kafka`
- `GET /api/notifications/email/{id}`
- `GET /api/notifications/email/history`
- `POST /api/notifications/email/{id}/retry`
- `GET /api/notifications/email/failed`
- `GET /api/notifications/email/pending`
- `GET /api/audit`
- `GET /api/audit/{id}`
- `GET /api/audit/users/{userId}`
- `GET /api/audit/timeline`
- `GET /api/audit/summary`
- `POST /api/reports/account-statements`
- `POST /api/reports/transactions`
- `POST /api/reports/customers`
- `POST /api/reports/cards`
- `POST /api/reports/loans`
- `POST /api/reports/bill-payments`
- `POST /api/reports/schedules`
- `POST /api/reports/admin-overview`
- `POST /api/reports/audit`
- `GET /api/reports/history`
- `GET /api/reports/{id}`
- `GET /api/reports/{id}/download`
- `GET /api/admin/dashboard`
- `GET /api/admin/customers`
- `GET /api/admin/accounts`
- `GET /api/admin/beneficiaries`
- `GET /api/admin/transactions`
- `GET /api/admin/workflows`
- `GET /api/admin/loans`
- `GET /api/admin/cards`
- `GET /api/admin/branches`
- `GET /api/admin/bill-payments`
- `GET /api/admin/schedules`
- `GET /api/admin/audit-summary`
- `GET /api/admin/system`
- `GET /api/admin/search`

## 23. Frontend integration checklist

- Route all calls through the Gateway base URL.
- Attach the bearer token to every API except register/login.
- Never expose `INTERNAL_API_KEY`, SMTP credentials, JWT secret, or 2FA encryption key in frontend builds.
- Treat `401`, `403`, and `503` differently.
- Generate one idempotency key per confirmed workflow/report action and retain it across safe retries.
- Never parse a `204` response as JSON.
- Model both standard ApiResponse envelopes and raw Phase 5 DTOs.
- Use zero-based pagination and honor server limits.
- URL-encode every query and path value.
- Use ISO-8601 timestamps and IANA time zones.
- Use decimal-safe handling for money.
- Poll report jobs with capped backoff and terminate polling at terminal state.
- Download reports as Blob/binary and honor `Content-Disposition`.
- Upload KYC documents with `FormData`; do not manually set the multipart `Content-Type` boundary.
- Fetch protected KYC document URLs as Blob/binary with the appropriate customer or admin bearer token.
- Keep KYC status and document metadata as separate frontend state and refresh both after replacement.
- Display masked account/card/loan values exactly as returned; never attempt to reconstruct secrets.
- Refresh authoritative balances and histories after completed workflows.
- Do not replace an unresolved workflow with a new idempotency key.
- Do not call or document `/internal/**` endpoints as client-accessible APIs.
- Do not use notification test endpoints as normal customer messaging UI.
