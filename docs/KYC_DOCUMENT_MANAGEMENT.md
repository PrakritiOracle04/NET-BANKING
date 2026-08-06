# KYC Document Management

## Purpose

KYC document management is implemented by `customer-service`. It lets customers upload supporting identity documents and lets administrators securely review those documents before approving or rejecting the customer's KYC record.

Files are stored in a persistent Podman volume. The database stores document metadata, the internal file path and a protected HTTP content URL. The storage volume is never exposed directly through the API gateway.

This is intentionally a college-project design. It provides authentication, ownership checks, validation, persistence and a clear review workflow without cloud object storage, malware scanning, signed URLs or file encryption.

## Supported documents

| Document type | Required for verification | Supported formats |
|---|---:|---|
| `AADHAAR` | Yes | PDF, JPG/JPEG, PNG |
| `PAN` | Yes | PDF, JPG/JPEG, PNG |
| `ADDRESS_PROOF` | No | PDF, JPG/JPEG, PNG |

Only one current document of each type is stored for a customer. Uploading the same type again replaces the existing file while retaining the same document identity and content URL.

## Architecture

```text
Frontend
   |
   | JWT + multipart/HTTP request
   v
API Gateway :8080
   |
   v
Customer Service :8083
   |                      |
   | metadata             | file bytes
   v                      v
KYC_DOCUMENTS        Podman named volume
Oracle DB            net-banking-kyc-document-data
```

The database URL is not a link to the physical volume. It points back to an authenticated backend endpoint. Customer Service checks the JWT and document ownership before streaming the file.

## Authentication and authorization

Every route in this document requires:

```http
Authorization: Bearer <JWT>
```

Customer rules:

- The user ID is taken from the JWT subject. It is never accepted from the customer request body.
- A customer can list, view, replace or delete only their own documents.
- Asking for another customer's `documentId` returns `404`, avoiding disclosure of whether that document exists.

Administrator rules:

- Admin routes require the `ADMIN` role.
- Every admin document route includes the target `userId`.
- Customer Service verifies that the requested `documentId` belongs to that `userId`.
- The review queue contains metadata only. File bytes are loaded only after an administrator selects a customer.

## Frontend KYC lifecycle

### 1. Authenticate the customer

Call the existing login API and retain the bearer token.

```http
POST /api/auth/login
```

All subsequent customer requests use this token.

### 2. Complete the customer profile

```http
PUT /api/customers/me
```

The customer profile should be completed before account opening. This step is separate from KYC document upload.

### 3. Submit KYC identity details

```http
PUT /api/customers/me/kyc
Content-Type: application/json
```

```json
{
  "aadhaarNumber": "123456781234",
  "panNumber": "ABCDE1234F"
}
```

This creates or updates the customer's `CUSTOMER_KYC` record and places it in `PENDING`. It must happen before file upload because every document has a foreign key to that KYC record.

### 4. Upload required documents

Upload Aadhaar and PAN separately:

```http
POST /api/customers/me/kyc/documents
Content-Type: multipart/form-data
```

Multipart fields:

```text
documentType=AADHAAR
file=<aadhaar.pdf>
```

Repeat with:

```text
documentType=PAN
file=<pan.png>
```

Address proof is optional:

```text
documentType=ADDRESS_PROOF
file=<address-proof.jpg>
```

### 5. Confirm the uploaded documents

```http
GET /api/customers/me/kyc/documents
```

The frontend should confirm that `AADHAAR` and `PAN` are present before showing the submission as ready for admin review.

### 6. Administrator loads the review queue

```http
GET /api/customers/kyc/reviews?status=PENDING
Authorization: Bearer <admin-jwt>
```

The response identifies customers awaiting review and lists their uploaded document types. It does not stream every file.

Available filters are:

- `PENDING`
- `VERIFIED`
- `REJECTED`
- No `status` parameter to retrieve all KYC summaries

### 7. Administrator loads one customer's documents

```http
GET /api/customers/{userId}/kyc/documents
Authorization: Bearer <admin-jwt>
```

The administrator uses the `userId` selected from the review queue. Every returned item contains an admin-specific protected content URL.

### 8. Administrator views each file

```http
GET /api/customers/{userId}/kyc/documents/{documentId}/content
Authorization: Bearer <admin-jwt>
```

The backend checks both `userId` and `documentId` before streaming the file.

### 9A. Administrator approves KYC

```http
PUT /api/customers/{userId}/kyc/status
Content-Type: application/json
Authorization: Bearer <admin-jwt>
```

```json
{
  "status": "VERIFIED",
  "rejectionReason": null
}
```

Approval is rejected unless both `AADHAAR` and `PAN` documents exist. After approval, the customer cannot replace or delete documents.

### 9B. Administrator rejects KYC

```json
{
  "status": "REJECTED",
  "rejectionReason": "PAN image is unreadable"
}
```

A rejection reason is mandatory.

### 10. Customer corrects a rejected document

The customer uploads the rejected document type again using the same upload API. Replacement automatically changes the overall KYC status from `REJECTED` to `PENDING` and clears the old rejection reason.

The administrator then sees the customer in the `PENDING` review queue and repeats the review.

## Customer API reference

### Upload or replace a document

```http
POST /api/customers/me/kyc/documents
```

Example curl:

```bash
curl --location 'http://localhost:8080/api/customers/me/kyc/documents' \
  --header 'Authorization: Bearer <customer-jwt>' \
  --form 'documentType=AADHAAR' \
  --form 'file=@"C:/Users/Gokul/Documents/aadhaar.pdf"'
```

Success: `201 Created`

```json
{
  "success": true,
  "message": "KYC document uploaded",
  "data": {
    "documentId": "94d426a1-0d85-4fc5-975c-62974c03c738",
    "userId": "cccbbdb0-d406-4076-94af-d440e055bedf",
    "documentType": "AADHAAR",
    "originalFileName": "aadhaar.pdf",
    "contentType": "application/pdf",
    "fileSize": 284312,
    "documentUrl": "http://localhost:8080/api/customers/me/kyc/documents/94d426a1-0d85-4fc5-975c-62974c03c738/content",
    "uploadedAt": "2026-08-06T10:30:00Z",
    "updatedAt": "2026-08-06T10:30:00Z"
  }
}
```

### List the current customer's documents

```http
GET /api/customers/me/kyc/documents
```

Success: `200 OK`. The `data` property is an array of document metadata. Physical file paths and generated storage filenames are never returned.

### Display or download a customer document

```http
GET /api/customers/me/kyc/documents/{documentId}/content
```

Success: `200 OK` with the original media type and an inline `Content-Disposition` header.

A normal `<img src="...">` cannot attach an Authorization header. The frontend should fetch the protected URL as a blob and create an object URL:

```javascript
const response = await fetch(document.documentUrl, {
  headers: { Authorization: `Bearer ${token}` }
});

if (!response.ok) {
  throw new Error("Unable to load KYC document");
}

const blob = await response.blob();
const objectUrl = URL.createObjectURL(blob);
// Use objectUrl as an image src or PDF viewer URL.
// Call URL.revokeObjectURL(objectUrl) when the view is closed.
```

### Delete a customer document

```http
DELETE /api/customers/me/kyc/documents/{documentId}
```

Success: `204 No Content`. The database record and physical file are removed. Deletion is blocked after KYC verification.

## Administrator API reference

### Review queue

```http
GET /api/customers/kyc/reviews?status=PENDING
```

Example response item:

```json
{
  "kycId": "c74d979b-e7fc-48bf-a439-f77d31311b90",
  "userId": "cccbbdb0-d406-4076-94af-d440e055bedf",
  "status": "PENDING",
  "rejectionReason": null,
  "documentCount": 2,
  "uploadedDocumentTypes": ["AADHAAR", "PAN"],
  "createdAt": "2026-08-06T10:00:00Z",
  "updatedAt": "2026-08-06T10:30:00Z"
}
```

### List documents for one customer

```http
GET /api/customers/{userId}/kyc/documents
```

This route always requires `userId`. Its response URLs point to the admin content route rather than the customer `/me` route.

### View one customer's document

```http
GET /api/customers/{userId}/kyc/documents/{documentId}/content
```

Supplying a document from a different user returns `404 Not Found`.

## Status codes

| Status | Meaning |
|---:|---|
| `200` | Metadata returned or file streamed |
| `201` | Document uploaded or replaced |
| `204` | Document deleted |
| `400` | Empty file, invalid type/signature, invalid lifecycle operation or missing required verification documents |
| `401` | JWT missing, invalid or expired |
| `403` | Authenticated user lacks the ADMIN role |
| `404` | KYC/document not found or document does not belong to the requested user |
| `413` | Multipart upload exceeds the configured size |
| `500` | Persistent storage operation failed |

## Database model

`KYC_DOCUMENTS` contains:

- `DOCUMENT_ID` primary key
- `KYC_ID` foreign key to `CUSTOMER_KYC.KYC_ID`
- `USER_ID`
- `DOCUMENT_TYPE`
- `ORIGINAL_FILE_NAME`
- `STORED_FILE_NAME`
- `FILE_PATH`
- `DOCUMENT_URL`
- `CONTENT_TYPE`
- `FILE_SIZE`
- `UPLOADED_AT`
- `UPDATED_AT`

The unique constraint on `(USER_ID, DOCUMENT_TYPE)` enforces replacement semantics. Indexes support lookups by user and KYC record.

Hibernate creates or updates this table at Customer Service startup because the project currently uses `spring.jpa.hibernate.ddl-auto=update`.

## Persistent volume and configuration

The Compose named volume is:

```text
net-banking-kyc-document-data
```

It is mounted into `customer-service` at the path configured by `.env`:

```dotenv
KYC_DOCUMENT_STORAGE_PATH=/var/lib/net-banking/kyc-documents
KYC_DOCUMENT_MAX_FILE_SIZE=5MB
KYC_DOCUMENT_MAX_REQUEST_SIZE=6MB
PUBLIC_API_BASE_URL=http://localhost:8080
```

The multipart request limit is slightly larger than the file limit so multipart headers do not cause a valid 5 MB file to be rejected.

`PUBLIC_API_BASE_URL` must be changed when the gateway is accessed through a different host or port. It controls the protected HTTP URLs stored in the database and returned to the frontend.

Rebuilding or replacing the Customer Service container does not delete the named volume. Removing the named volume explicitly will delete the stored files while leaving database metadata behind.

## Manual verification checklist

1. Start the stack with `podman-compose up -d --build`.
2. Register/login a customer and obtain a customer JWT.
3. Complete the customer profile.
4. Submit Aadhaar/PAN KYC details.
5. Upload valid Aadhaar and PAN documents.
6. List customer documents and confirm protected URLs are present.
7. Fetch a content URL with the customer JWT and confirm the PDF/image opens.
8. Confirm another customer cannot fetch that `documentId`.
9. Login as an administrator.
10. Query the `PENDING` review queue.
11. List documents using the selected customer's `userId`.
12. Fetch both documents with the admin JWT.
13. Approve the KYC and confirm uploads/deletes are then blocked.
14. In a separate test, reject KYC and replace one document.
15. Confirm the KYC automatically returns to `PENDING`.
16. Restart Customer Service and confirm the file remains available from the Podman volume.

## Explicitly out of scope

- Cloud object storage
- Public/static file serving
- Encryption of document files at rest
- Antivirus or malware scanning
- Signed or expiring URLs
- Document version history
- Automated OCR
- Production retention and archival policies
