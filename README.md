# Internet Banking Platform — Phase 1

Phase 1 is a Spring Boot 3 / Java 17 microservice foundation for Internet Banking. The repository root is the Maven aggregator; each service below is independently runnable.

| Module | Port | Responsibility |
| --- | ---: | --- |
| `api-gateway` | 8080 | CORS, request logging, and API routing |
| `shared-kernel` | — | Reusable response contracts, constants, and validation utilities |
| `auth-service` | 8081 | Registration, BCrypt login, JWT, RBAC, and user sessions |
| `twofa-service` | 8082 | TOTP setup, QR generation, OTP verification, and encrypted factors |
| `customer-service` | 8083 | Customer profile lifecycle |
| `branch-service` | 8084 | Read-only branch lookup APIs |

The original entity files remain in `src/main/java/com/oracle/banking/entity` as the requested reference set. Phase 1 service models are implemented only inside their owning service:

- Auth: `AppUser`, `Role`, `UserSession`
- 2FA: `AuthFactor`
- Customer: `CustomerProfile`
- Branch: `Branch`

No service accesses another service’s repository or table. Auth-to-customer creation and auth-to-2FA checks use REST endpoints protected with an internal API key.

## Configuration

Every service has an `application.yml` with Oracle 23ai connection settings controlled by environment variables. Set distinct database credentials for:

- `AUTH_DB_*`
- `TWOFA_DB_*`
- `CUSTOMER_DB_*`
- `BRANCH_DB_*`

Set the same strong Base64 JWT secret in `JWT_SECRET` for all protected services. Set a shared, non-default `INTERNAL_API_KEY` for auth, 2FA, and customer communication. Set `TWOFA_ENCRYPTION_KEY` to a separate 256-bit Base64 AES key in production.

## Build and run

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd -DskipTests package
```

Run each application from its module:

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run
```

Swagger is available at `/swagger-ui` on each service. Send external requests through the gateway:

- `/api/auth/**`
- `/api/2fa/**`
- `/api/customers/**`
- `/api/branches/**`

The gateway forwards these paths to ports 8081–8084 respectively.
