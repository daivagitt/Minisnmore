# Local development (microservices)

## 1. Infrastructure

```bash
docker compose -f docker/microservices.yml up -d
```

| Service | Port |
|---------|------|
| Gateway | 8080 |
| Catalog core (`backend/`) | 8084 (internal APIs only) |
| Store (`store/`) | 8081 |
| Commerce (`commerce/`) | 8082 |
| Integration (`integration/`) | 8083 |
| Consul | 8500 |
| Postgres (catalog) | 5432 |
| Postgres (gateway) | 5433 |
| Redis | 6379 |

## 2. Start Java services

**One command:**

```bash
bash scripts/dev-start-microservices.sh
```

**Or two terminals:**

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd gateway && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Dev gateway routes `/api/v1/**` directly to `http://127.0.0.1:8081` (no Consul required for routing).

## 3. Frontend

```bash
npm run dev
```

Vite proxies `/api`, `/oauth2`, `/login/oauth2` → **http://127.0.0.1:8080**.

## 4. Smoke checks

```bash
bash deploy/scripts/health-check-microservices.sh
bash scripts/verify-frontend-api-contract.sh
```

## 5. Default admin (JHipster faker)

After first start with `dev,faker` Liquibase contexts, try:

- User: `admin` / `admin` (gateway DB)
- API login: `POST http://localhost:8080/api/authenticate`

## 6. After `jhipster` in backend/

```bash
bash scripts/post-jhipster-cleanup.sh
cd backend && ./mvnw compile -DskipTests
```

See [JHIPSTER_REGEN.md](./JHIPSTER_REGEN.md).

## Without Docker

You can still run **automated tests** and **compile** services. You cannot start the full microservice stack or JHipster `*IT*` tests without Postgres (and Testcontainers needs Docker).

### One command (recommended)

```bash
bash scripts/test-local-no-docker.sh
```

### Or step by step

```bash
# Frontend — mocks API; no Java services
npm run test

# Gateway + catalog — unit tests only (*IT* excluded by Surefire)
cd gateway && ./mvnw test
cd ../backend && ./mvnw test
```

Compile only:

```bash
cd backend && ./mvnw compile -DskipTests
cd gateway && ./mvnw compile -DskipTests
```

### What still needs Docker or native installs

| Goal | Without Docker |
|------|----------------|
| Module smoke (`verify-frontend-api-contract.sh`) | Install Postgres (5432 + 5433), Redis, Consul locally, or use Docker |
| `mvn verify` on catalog (JHipster `*IT*`) | Docker (Testcontainers) or skip: `./mvnw test` only |
| Zoho / checkout E2E | Running API + env vars — see [zoho-integration-test-scenarios.md](./zoho-integration-test-scenarios.md) |

### Native Postgres (optional, to run apps without Docker)

Create DBs matching `application-dev.yml`:

- Catalog: `localhost:5432`, user `miniandmore`, DB `miniandmore`
- Gateway: `localhost:5433`, user `gateway`, DB `gateway`
- Redis: `localhost:6379`
- Consul: `localhost:8500` (optional in dev — gateway routes `/api/v1` directly to `:8081`)

Disable schedulers in dev if Zoho/Razorpay keys are unset.

## References

- [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md)
- [MODULES_TESTING.md](./MODULES_TESTING.md)
