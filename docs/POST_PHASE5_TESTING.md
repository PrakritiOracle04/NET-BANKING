# Post Phase 5 Manual Testing Guide

Base URL:

```text
http://localhost:8080
```

For protected routes, use:

```http
Authorization: Bearer <token>
Content-Type: application/json
```

## 1. Password recovery

Request OTP:

```http
POST /api/auth/password-reset/request
```

```json
{
  "email": "customer_demo@example.com"
}
```

Verify OTP:

```http
POST /api/auth/password-reset/verify
```

```json
{
  "email": "customer_demo@example.com",
  "otpCode": "123456"
}
```

Confirm new password:

```http
POST /api/auth/password-reset/confirm
```

```json
{
  "resetToken": "paste-reset-token-from-verify",
  "newPassword": "NewPass@12345",
  "confirmPassword": "NewPass@12345"
}
```

Expected:

- Request returns a generic accepted message.
- Verify returns `data.resetToken`.
- Confirm succeeds and invalidates old login sessions.

Timing notes:

- Use the newest OTP immediately. Local default OTP TTL is `PASSWORD_RESET_OTP_TTL_SECONDS=600`, so the code expires after 10 minutes.
- Requesting another OTP expires the previous pending OTP for that user.
- Use `data.resetToken` immediately after verification. Local default reset-token TTL is also 10 minutes.
- After confirm, all old sessions for the user are invalidated; log in again with the new password before testing protected APIs.

## 2. Card application flow

Card products:

```http
GET /api/cards/products
```

Submit customer application:

```http
POST /api/cards/applications
```

```json
{
  "accountId": "paste-customer-account-id",
  "cardProduct": "GOLD",
  "annualIncome": 500000,
  "occupation": "Software Engineer",
  "deliveryAddress": "Home address",
  "requestedDailyLimit": 25000
}
```

List my applications:

```http
GET /api/cards/applications
```

Admin list pending applications:

```http
GET /api/cards/admin/applications?status=PENDING&page=0&size=50
```

Admin approve:

```http
POST /api/cards/admin/applications/{applicationId}/approve
```

```json
{
  "approvedDailyLimit": 25000,
  "notes": "Approved after review"
}
```

Expected:

- Approval returns `issuedCardId`.
- The issued card is `INACTIVE`.
- Customer can activate it with `POST /api/cards/{issuedCardId}/activate`.

Admin reject alternative:

```http
POST /api/cards/admin/applications/{applicationId}/reject
```

```json
{
  "reason": "Eligibility criteria not met"
}
```

## 3. Loan application flow

Submit customer application:

```http
POST /api/loans/applications
```

```json
{
  "linkedAccountId": "paste-customer-account-id",
  "loanType": "HOME",
  "requestedAmount": 100000,
  "tenureMonths": 12,
  "monthlyIncome": 50000,
  "employmentType": "SALARIED",
  "purpose": "Home renovation"
}
```

List my applications:

```http
GET /api/loans/applications
```

Admin list pending applications:

```http
GET /api/loans/admin/applications?status=PENDING&page=0&size=50
```

Admin approve:

```http
POST /api/loans/admin/applications/{applicationId}/approve
```

```json
{
  "approvedAmount": 100000,
  "annualInterestRate": 10.5,
  "tenureMonths": 12,
  "startDate": "2026-08-05",
  "notes": "Approved after manual review"
}
```

Expected:

- Approval returns `issuedLoanId`.
- `GET /api/loans/{issuedLoanId}` returns the active loan.
- `GET /api/loans/{issuedLoanId}/schedule` returns EMI rows.

Admin reject alternative:

```http
POST /api/loans/admin/applications/{applicationId}/reject
```

```json
{
  "reason": "Eligibility criteria not met"
}
```

## Runtime env additions

Add these to local `.env`; use safe production values outside local dev:

```text
PASSWORD_RESET_OTP_HMAC_KEY=<base64-or-raw-32-byte-minimum-key>
PASSWORD_RESET_OTP_TTL_SECONDS=600
PASSWORD_RESET_TOKEN_TTL_SECONDS=600
PASSWORD_RESET_MAX_ATTEMPTS=5
PASSWORD_RESET_RESEND_COOLDOWN_SECONDS=60
PASSWORD_RESET_RETENTION_DAYS=7
PASSWORD_RESET_CLEANUP_DELAY_MS=3600000

CARD_CLASSIC_MIN_ANNUAL_INCOME=0
CARD_GOLD_MIN_ANNUAL_INCOME=300000
CARD_PLATINUM_MIN_ANNUAL_INCOME=800000
CARD_CLASSIC_DEFAULT_DAILY_LIMIT=10000
CARD_GOLD_DEFAULT_DAILY_LIMIT=25000
CARD_PLATINUM_DEFAULT_DAILY_LIMIT=50000

CREDIT_CARD_DEFAULT_BILLING_CYCLE_DAY=5
CREDIT_CARD_CLASSIC_MIN_ANNUAL_INCOME=300000
CREDIT_CARD_GOLD_MIN_ANNUAL_INCOME=600000
CREDIT_CARD_PLATINUM_MIN_ANNUAL_INCOME=1200000
CREDIT_CARD_CLASSIC_DEFAULT_LIMIT=50000
CREDIT_CARD_GOLD_DEFAULT_LIMIT=100000
CREDIT_CARD_PLATINUM_DEFAULT_LIMIT=250000

CARD_APPLICATION_SUBMITTED_TOPIC=card-application-submitted
CARD_APPLICATION_APPROVED_TOPIC=card-application-approved
CARD_APPLICATION_REJECTED_TOPIC=card-application-rejected

LOAN_MIN_MONTHLY_INCOME=10000
LOAN_MAX_TENURE_MONTHS=360
LOAN_HOME_MAX_AMOUNT=10000000
LOAN_VEHICLE_MAX_AMOUNT=2000000
LOAN_PERSONAL_MAX_AMOUNT=1000000
LOAN_EDUCATION_MAX_AMOUNT=3000000
LOAN_BUSINESS_MAX_AMOUNT=5000000
LOAN_APPLICATION_SUBMITTED_TOPIC=loan-application-submitted
LOAN_APPLICATION_APPROVED_TOPIC=loan-application-approved
LOAN_APPLICATION_REJECTED_TOPIC=loan-application-rejected
```

## Local legacy database cleanup notes

These are only for local databases that were created from older Phase 1/2 schemas. They are not application migrations.

If login/session validation fails after pulling newer code, confirm `APP_USER` has the current role column populated:

```sql
ALTER TABLE app_user ADD role_id VARCHAR2(36 CHAR);

UPDATE app_user
SET role_id = role_role_id
WHERE role_id IS NULL;

COMMIT;
```

If new account creation through the internal account endpoint fails with a generic 500, check whether the legacy username column is still `NOT NULL`:

```sql
SELECT constraint_name, constraint_type, search_condition
FROM user_constraints
WHERE table_name = 'ACCOUNTS'
ORDER BY constraint_name;
```

If `CUSTOMER_USERNAME` is still required, relax it because the current account entity uses `CUSTOMER_USER_ID` as source of truth:

```sql
ALTER TABLE accounts MODIFY customer_username NULL;
COMMIT;
```

For any remaining local 500 during insert/update, check the owning service logs first:

```powershell
podman logs net-banking_auth-service_1 --tail 150
podman logs net-banking_account-service_1 --tail 150
podman logs net-banking_loan-service_1 --tail 150
```
