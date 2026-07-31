# Internet Banking Platform

This is a Spring Boot 3 / Java 17 microservice foundation for Internet Banking. The repository root is the Maven aggregator; each service below is independently runnable.

| Module | Port | Responsibility |
| --- | ---: | --- |
| `api-gateway` | 8080 | CORS, request logging, and API routing |
| `shared-kernel` | - | Reusable response contracts, constants, and validation utilities |
| `auth-service` | 8081 | Registration, BCrypt login, JWT, RBAC, and user sessions |
| `twofa-service` | 8082 | TOTP setup, QR generation, OTP verification, and encrypted factors |
| `customer-service` | 8083 | Customer profile lifecycle |
| `branch-service` | 8084 | Read-only branch lookup APIs |
| `account-service` | 8085 | Accounts, balances, status, credit/debit ownership |
| `beneficiary-service` | 8086 | Beneficiary CRUD, status, transfer verification |
| `transaction-service` | 8087 | Transaction records, history, search, statements |
| `banking-workflow-service` | 8088 | Deposit, withdrawal, and transfer orchestration |

The original entity files remain in `src/main/java/com/oracle/banking/entity` and `legacy-entity-reference` as reference material. Service models are implemented only inside their owning service.

No service accesses another service's repository or table. Cross-service work uses REST endpoints protected with an internal API key. Customer ownership uses the immutable Auth `userId`, never the mutable username. The banking workflow service owns its Saga state table, coordinates synchronous REST calls, and publishes Kafka events only after successful business operations.

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

Set the same strong Base64 JWT secret in `JWT_SECRET` for all protected services. Set a shared, non-default `INTERNAL_API_KEY` for internal service communication. Set `TWOFA_ENCRYPTION_KEY` to a separate 256-bit Base64 AES key in production.

Kafka and Kafbat Kafka UI are included in `compose.yaml`. Banking containers connect through `kafka:29092`; host-side Kafka tools can use `localhost:9092`. Kafka UI is available at `http://localhost:8081`.

Use `.env.example` as the key template for local setup. Create the Oracle user/schema yourself, then start the services; there are no manual table migration scripts for Phase 2.

## Build and Run

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd -DskipTests package
```

Start all services:

```powershell
.\run-all-services.ps1
```

Stop all services:

```powershell
.\stop-all-services.ps1
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

The gateway forwards these paths to their owning services on ports 8081-8088.
