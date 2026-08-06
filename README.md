# Internet Banking Platform

This is a Spring Boot 3 / Java 17 microservice foundation for Internet Banking. The repository root is the Maven aggregator; each service below is independently runnable.

| Module | Port | Responsibility |
| --- | ---: | --- |
| `api-gateway` | 8080 | CORS, active-session enforcement, request logging, and API routing |
| `shared-kernel` | - | Reusable response contracts, constants, and validation utilities |
| `auth-service` | 8081 | Registration, BCrypt login, JWT, RBAC, and user sessions |
| `twofa-service` | 8082 | TOTP setup, QR generation, OTP verification, and encrypted factors |
| `customer-service` | 8083 | Customer profile lifecycle and encrypted KYC |
| `branch-service` | 8084 | Read-only branch lookup APIs |
| `account-service` | 8085 | Generated accounts, balances, status, credit/debit ownership |
| `beneficiary-service` | 8086 | Beneficiary CRUD, status, transfer verification |
| `transaction-service` | 8087 | Transaction records, history, search, statements |
| `banking-workflow-service` | 8088 | Account-opening, deposit, withdrawal, and transfer orchestration |
| `notification-service` | 8089 | Kafka-backed email notifications and delivery history |
| `billpayment-service` | 8090 | Biller catalog, customer billers, and payment history |
| `card-service` | 8091 | Secure card issuance, state, and daily limits |
| `loan-service` | 8092 | Loan registration, EMI schedules, balances, and repayment history |
| `banking-scheduler-service` | 8093 | Scheduled bill payments and protected maintenance schedules |
| `audit-service` | 8094 | Append-only sanitized Kafka audit history and ADMIN queries |
| `report-service` | 8095 | Asynchronous CSV/PDF generation and authenticated downloads |
| `admin-service` | 8096 | Stateless read-only operational aggregation for administrators |

The original entity files remain in `src/main/java/com/oracle/banking/entity` and `legacy-entity-reference` as reference material. Service models are implemented only inside their owning service. See `DATA_OWNERSHIP.md` for the field-level ownership rules and `API_DOCUMENTATION.md` for complete routes and flows.

No service accesses another service's repository or table. Cross-service work uses REST endpoints protected with an internal API key. Customer ownership uses the immutable Auth `userId`, never the mutable username. Auth owns verified email/phone; Customer owns profile and encrypted KYC; Account owns generated account numbers. The banking workflow service owns its Saga state table, coordinates synchronous REST calls, and publishes Kafka events only after successful business operations.

## Configuration

Every data-owning service has an `application.yml` with Oracle connection settings controlled by environment variables. For local development, all services can point to the same fresh Oracle schema/user. The tables are created and updated from the Java entity classes through Hibernate `ddl-auto: update`.

Provide database credentials for:

- `AUTH_DB_*`
- `TWOFA_DB_*`
- `CUSTOMER_DB_*`
- `BRANCH_DB_*`
- `ACCOUNT_DB_*`
- `BENEFICIARY_DB_*`
- `TRANSACTION_DB_*`
- `BILLPAYMENT_DB_*`
- `CARD_DB_*`
- `LOAN_DB_*`
- `SCHEDULER_DB_*`
- `NOTIFICATION_DB_*`
- `AUDIT_DB_*`
- `REPORT_DB_*`

Set the same strong Base64 JWT secret in `JWT_SECRET` for all protected services. Set a shared, non-default `INTERNAL_API_KEY` for internal service communication. Set `TWOFA_ENCRYPTION_KEY` to a separate 256-bit Base64 AES key in production.
Set `CARD_ENCRYPTION_KEY` to another independent Base64 256-bit key. Card PAN values are encrypted with AES-GCM and are exposed only as masked values.
Card debit and credit-card product eligibility/limits are environment-driven through `CARD_*` and `CREDIT_CARD_*` values; keep local values in the ignored `.env`.

Every login creates a revocable Auth session and places its identifier in the JWT `sid` claim. The Gateway validates that session with Auth before routing every protected `/api/**` request. See `SESSION_MANAGEMENT.md` for login, expiry, logout, logout-all, failure behavior, and client requirements.

Kafka and Kafbat Kafka UI are included in `compose.yaml`. Banking containers connect through `kafka:29092`; host-side Kafka tools can use `localhost:9092`. Kafka UI is available at `http://localhost:8081`.

Keep real configuration in the ignored root `.env`. It is the single source of truth for every environment-specific value referenced by service YAML and Compose; YAML placeholders intentionally have no fallback values, so a missing variable fails configuration instead of silently using a development default. The checked-in YAML retains only structural application settings such as service ports and Spring/JPA behavior. Create the Oracle user/schema yourself, then start the services. JPA entities are currently the development DDL source of truth; use a fresh schema after structural changes because `ddl-auto: update` is not a production migration engine.

## Build and Run

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd -DskipTests package
```

Start all services with Podman Compose:

```powershell
podman-compose up -d --build
```

Stop all services:

```powershell
podman-compose down
```

Swagger is available at `/swagger-ui` on each service. Send external requests through the gateway:

- `/api/auth/**`
- `/api/2fa/**`
- `/api/customers/**`
- `/api/branches/**`
- `/api/accounts/**`
- `/api/beneficiaries/**`
- `/api/transactions/**`
- `/api/banking/**`
- `/api/billers/**`
- `/api/bill-payments/**`
- `/api/cards/**`
- `/api/loans/**`
- `/api/schedules/**`
- `/api/notifications/**`
- `/api/audit/**`
- `/api/reports/**`
- `/api/admin/**`

The gateway forwards these paths to their owning services on ports 8081-8096. See `FRONTEND_API_CONTRACT.md` for frontend-ready URLs, request bodies, auth rules, and smoke-test status. Phase 5 operations, audit, reporting, storage, and troubleshooting are documented in `PHASE5_OPERATIONS_GUIDE.md`.
