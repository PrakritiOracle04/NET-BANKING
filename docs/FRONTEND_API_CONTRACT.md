# Frontend API Contract

Use the API Gateway for every frontend request:

```text
http://localhost:8080
```

All protected routes require:

```http
Authorization: Bearer <token>
Content-Type: application/json
```

Mutating banking workflow routes also require a unique client-generated idempotency key:

```http
Idempotency-Key: <stable-key-per-user-action>
```

Successful responses use:

```json
{
  "success": true,
  "message": "Operation completed",
  "data": {},
  "timestamp": "2026-08-04T05:00:00Z"
}
```

Errors use:

```json
{
  "success": false,
  "message": "Access denied",
  "path": "/api/example",
  "timestamp": "2026-08-04T05:00:00Z"
}
```

## Auth

### Register

```http
POST /api/auth/register
```

```json
{
  "username": "customer_demo",
  "email": "customer_demo@example.com",
  "phone": "9876543210",
  "password": "<password>",
  "fullName": "Customer Demo"
}
```

### Login

```http
POST /api/auth/login
```

```json
{
  "username": "customer_demo",
  "password": "<password>"
}
```

Use `data.token` as the bearer token.

### Password Recovery

Request a one-time password. The response is intentionally generic so unknown emails are not revealed.

```http
POST /api/auth/password-reset/request
```

```json
{
  "email": "customer_demo@example.com"
}
```

Verify the OTP from email:

```http
POST /api/auth/password-reset/verify
```

```json
{
  "email": "customer_demo@example.com",
  "otpCode": "123456"
}
```

Use `data.resetToken` from the verify response:

```http
POST /api/auth/password-reset/confirm
```

```json
{
  "resetToken": "reset-token-from-verify",
  "newPassword": "<new-password>",
  "confirmPassword": "<new-password>"
}
```

## Accounts And Banking

### List Accounts

```http
GET /api/accounts
```

### Account Details

```http
GET /api/accounts/{accountId}
```

### Balance

```http
GET /api/accounts/{accountId}/balance
```

### Mini Statement

```http
GET /api/accounts/{accountId}/mini-statement?limit=10
```

### Deposit

```http
POST /api/banking/deposit
Idempotency-Key: deposit-<unique-value>
```

```json
{
  "accountId": "bb7f8fcc-3c62-4d2b-a066-f7e4b86d0dbf",
  "amount": 1000,
  "description": "Frontend deposit"
}
```

### Withdraw

```http
POST /api/banking/withdraw
Idempotency-Key: withdraw-<unique-value>
```

```json
{
  "accountId": "bb7f8fcc-3c62-4d2b-a066-f7e4b86d0dbf",
  "amount": 500,
  "description": "Frontend withdrawal"
}
```

### Transfer

```http
POST /api/banking/transfer
Idempotency-Key: transfer-<unique-value>
```

```json
{
  "sourceAccountId": "source-account-id",
  "destinationAccountNumber": "destination-account-number",
  "amount": 1000,
  "description": "Frontend transfer"
}
```

## Billers And Bill Payments

### Catalog

```http
GET /api/billers/catalog
```

Optional category filter:

```http
GET /api/billers/catalog?category=ELECTRICITY
```

### Register Customer Biller

```http
POST /api/billers
```

```json
{
  "billerId": "catalog-biller-id",
  "consumerReference": "ELEC-CONSUMER-001",
  "nickname": "Home Electricity"
}
```

### List Customer Billers

```http
GET /api/billers
```

### Direct Bill Payment

```http
POST /api/banking/bill-payments
Idempotency-Key: bill-payment-<unique-value>
```

```json
{
  "sourceAccountId": "bb7f8fcc-3c62-4d2b-a066-f7e4b86d0dbf",
  "customerBillerId": "75a07f42-a738-44c2-a09b-5959a5b40243",
  "amount": 100,
  "description": "Frontend bill payment"
}
```

Successful response includes `referenceNumber`, `billPaymentId`, and `transactionId`.

### Bill Payment History

```http
GET /api/bill-payments/history
```

Optional filters:

```http
GET /api/bill-payments/history?status=COMPLETED&sourceAccountId={accountId}&page=0&size=20
```

## Scheduled Bill Payments

### Create Schedule

`startAt` and `endAt` are UTC instants. Use the `timezone` field for recurrence calculations.

```http
POST /api/schedules
```

```json
{
  "scheduleType": "ONE_TIME",
  "sourceAccountId": "bb7f8fcc-3c62-4d2b-a066-f7e4b86d0dbf",
  "customerBillerId": "75a07f42-a738-44c2-a09b-5959a5b40243",
  "amount": 100,
  "description": "Scheduled bill payment",
  "timezone": "Asia/Kolkata",
  "startAt": "2026-08-04T05:07:00Z",
  "endAt": null,
  "maxRetries": 3
}
```

Supported schedule types are `ONE_TIME`, `DAILY`, `WEEKLY`, and `MONTHLY`.

### List Schedules

```http
GET /api/schedules
```

Admin filters:

```http
GET /api/schedules?customerUserId={userId}&status=ACTIVE
```

### Schedule Details

```http
GET /api/schedules/{scheduleId}
```

### Schedule Executions

```http
GET /api/schedules/{scheduleId}/executions
```

### Update Schedule

```http
PUT /api/schedules/{scheduleId}
```

Use the same body shape as create. Terminal schedules cannot be changed.

### Pause, Resume, Cancel

```http
POST /api/schedules/{scheduleId}/pause
POST /api/schedules/{scheduleId}/resume
DELETE /api/schedules/{scheduleId}
```

## Cards

### Card Products

Use this for the card product dropdown. The response includes debit and credit variants for each tier.

```http
GET /api/cards/products
```

Response `data` shape:

```json
[
  {
    "cardType": "DEBIT",
    "code": "GOLD",
    "label": "Gold Debit Card",
    "minimumAnnualIncome": 300000,
    "defaultDailyLimit": 25000,
    "defaultCreditLimit": null
  },
  {
    "cardType": "CREDIT",
    "code": "GOLD",
    "label": "Gold Credit Card",
    "minimumAnnualIncome": 600000,
    "defaultDailyLimit": 25000,
    "defaultCreditLimit": 100000
  }
]
```

### Submit Card Application

Customer route. The backend uses the logged-in user from the JWT, not a `customerUserId` from the body.

```http
POST /api/cards/applications
```

```json
{
  "accountId": "bb7f8fcc-3c62-4d2b-a066-f7e4b86d0dbf",
  "cardType": "CREDIT",
  "cardProduct": "GOLD",
  "annualIncome": 700000,
  "occupation": "Software Engineer",
  "deliveryAddress": "Home address",
  "requestedDailyLimit": 25000
}
```

`cardType` can be `DEBIT` or `CREDIT`. If omitted, the backend keeps old behavior and treats it as `DEBIT`.

Only one pending application or non-expired card can exist per account per card type. So one debit card and one credit card may exist for the same account, but not two active credit cards.

### My Card Applications

```http
GET /api/cards/applications
GET /api/cards/applications/{applicationId}
```

### Admin Card Application Review

Admin only.

```http
GET /api/cards/admin/applications?status=PENDING&page=0&size=50
POST /api/cards/admin/applications/{applicationId}/approve
POST /api/cards/admin/applications/{applicationId}/reject
```

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

Approval creates an `INACTIVE` card. The customer then activates it with `POST /api/cards/{cardId}/activate`.

For credit-card applications, approval also creates a credit-card account row using the configured product credit limit.

Direct `POST /api/cards` is retired from the public/frontend contract.

### List Cards

```http
GET /api/cards
```

Admin filter:

```http
GET /api/cards?customerUserId={userId}
```

### Card Details And Status

```http
GET /api/cards/{cardId}
GET /api/cards/{cardId}/status
```

Card numbers are always masked as `************1234`.

### Credit Card Account

Customer route:

```http
GET /api/cards/credit-accounts
GET /api/cards/{cardId}/credit-account
```

Admin filter:

```http
GET /api/cards/credit-accounts?customerUserId={userId}
```

The credit-card account response contains `creditLimit`, `availableCredit`, `outstandingBalance`, `billingCycleDay`, and `status`.

### Activate Card

Customer owner only.

```http
POST /api/cards/{cardId}/activate
```

### Update Limit

Customer owner only.

```http
PUT /api/cards/{cardId}/limit
```

```json
{
  "dailyTransactionLimit": 30000
}
```

### Block And Unblock

```http
POST /api/cards/{cardId}/block
```

```json
{
  "reason": "Customer request"
}
```

```http
POST /api/cards/{cardId}/unblock
```

## Loans

### Loan Type Catalog

Use this for the frontend loan-type dropdown.

```http
GET /api/loans/types
```

Response `data`:

```json
[
  { "code": "HOME", "label": "Home Loan" },
  { "code": "VEHICLE", "label": "Vehicle Loan" },
  { "code": "PERSONAL", "label": "Personal Loan" },
  { "code": "EDUCATION", "label": "Education Loan" },
  { "code": "BUSINESS", "label": "Business Loan" }
]
```

### Calculate EMI

```http
POST /api/loans/calculate
```

```json
{
  "loanAmount": 100000,
  "annualInterestRate": 10.5,
  "tenureMonths": 12,
  "startDate": "2026-08-04"
}
```

### Submit Loan Application

Customer route. The backend uses the logged-in user from the JWT, not a `customerUserId` from the body.

```http
POST /api/loans/applications
```

```json
{
  "linkedAccountId": "bb7f8fcc-3c62-4d2b-a066-f7e4b86d0dbf",
  "loanType": "HOME",
  "requestedAmount": 100000,
  "tenureMonths": 12,
  "monthlyIncome": 50000,
  "employmentType": "SALARIED",
  "purpose": "Home renovation"
}
```

### My Loan Applications

```http
GET /api/loans/applications
GET /api/loans/applications/{applicationId}
```

### Admin Loan Application Review

Admin only.

```http
GET /api/loans/admin/applications?status=PENDING&page=0&size=50
POST /api/loans/admin/applications/{applicationId}/approve
POST /api/loans/admin/applications/{applicationId}/reject
```

Approve body:

```json
{
  "approvedAmount": 100000,
  "annualInterestRate": 10.5,
  "tenureMonths": 12,
  "startDate": "2026-08-05",
  "notes": "Approved after manual review"
}
```

Reject body:

```json
{
  "reason": "Insufficient eligibility documents"
}
```

Approval creates the actual ACTIVE loan and EMI schedule.

Direct `POST /api/loans` remains backend/admin-only and should not be used by frontend customer flows.

### List Loans

```http
GET /api/loans
```

Admin filters:

```http
GET /api/loans?customerUserId={userId}&status=ACTIVE
```

### Loan Details, Balance, Schedule, History

```http
GET /api/loans/{loanId}
GET /api/loans/{loanId}/balance
GET /api/loans/{loanId}/schedule
GET /api/loans/{loanId}/history
```

### Update Loan Status

Admin only.

```http
PUT /api/loans/{loanId}/status
```

```json
{
  "status": "OVERDUE"
}
```

Valid statuses are `ACTIVE`, `OVERDUE`, `DEFAULTED`, and `CLOSED`.

Valid loan types are `HOME`, `VEHICLE`, `PERSONAL`, `EDUCATION`, and `BUSINESS`.

### Repay Loan

```http
POST /api/banking/loans/{loanId}/repay
Idempotency-Key: loan-repay-<unique-value>
```

```json
{
  "sourceAccountId": "bb7f8fcc-3c62-4d2b-a066-f7e4b86d0dbf",
  "amount": 1000,
  "description": "Frontend loan repayment"
}
```

## Notifications

### Email History

```http
GET /api/notifications/email/history
```

### Manual Test Email

```http
POST /api/notifications/email/test
```

```json
{
  "recipient": "customer_demo@example.com",
  "variables": {
    "message": "Manual notification test"
  }
}
```

`SENT`, `RETRYING`, and `FAILED` are valid delivery states. SMTP failure does not roll back banking operations.

### Kafka Test Email

```http
POST /api/notifications/email/test-kafka
```

```json
{
  "recipient": "customer_demo@example.com",
  "variables": {
    "message": "Kafka notification test"
  }
}
```

### Email Details And Retry

```http
GET /api/notifications/email/{notificationId}
POST /api/notifications/email/{notificationId}/retry
GET /api/notifications/email/pending
GET /api/notifications/email/failed
```

## Frontend Notes

- Store only the JWT and use it as a bearer token.
- Generate a new `Idempotency-Key` once per submit action for banking workflows.
- Do not retry a failed workflow with a different body and the same idempotency key.
- Show `409` messages directly for invalid state transitions such as activating an already active card.
- Treat `503` from banking workflows as "operation is recovering"; do not assume money was retained.
- Never ask users to enter account IDs manually in production UI; use account list responses.
- Never expose full card PAN, encrypted KYC values, internal API keys, or service ports to frontend users.

## Current Smoke-Test Status

Verified through the gateway on 2026-08-04:

| Area | Status |
| --- | --- |
| Direct bill payment | Passed |
| Scheduled bill payment | Passed |
| Card issue/list/activate/limit/block/unblock | Passed |
| Notification history/manual test | Passed |
| Transaction schema for `BILL_PAYMENT` | Fixed and verified |
