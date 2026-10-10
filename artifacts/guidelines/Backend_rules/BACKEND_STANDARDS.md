# 🖥️ BACKEND_STANDARDS.md

## 1. Purpose

This document defines **mandatory standards** for building backend services in this project (application servers, services, workers, and business logic).

⚠️ Any backend code that violates this document is considered **non-production-ready**.

All AI agents and developers **must read and follow this file before implementation**.

> For HTTP API contract, auth, security, and performance rules, also follow `api_rules/`. For data layer rules, follow `database_rules/DATABASE_STANDARDS.md`.

---

## 2. Core Principles (Non-Negotiable)

- **Separation of concerns** (clean layering)
- **Stateless services** (state lives in DB/cache, not in process memory)
- **Explicit over implicit**
- **Fail safely, never silently**
- **Secure by default, zero trust**
- **Idempotent & retry-safe where possible**
- **Observable** (structured logs, metrics, traces)

---

## 3. Architecture (MANDATORY)

**Layered architecture with strict boundaries:**

```
src/
│
├── routes/ (or controllers/)   # HTTP layer: parse, validate, respond
├── services/                   # business logic / use cases
├── repositories/               # data access (DB, external APIs)
├── models/ (or entities/)      # domain types & schemas
├── middleware/                 # auth, rate-limit, error handling
├── jobs/                       # background workers / queues
├── config/                     # typed config loaded from env
└── lib/                        # shared utilities, clients
```

**Dependency direction (one-way):**

```
Controller → Service → Repository → Data source
```

❌ **Controllers calling the database directly**
❌ **Business logic inside controllers or repositories**
❌ **Repositories containing business rules**

---

## 4. Layer Responsibilities

| Layer | Responsibility | Must NOT |
|-------|----------------|----------|
| Controller/Route | Validate input, call service, format response | Contain business logic or DB queries |
| Service | Business rules, orchestration, transactions | Know about HTTP req/res objects |
| Repository | Data access only | Make authorization decisions |
| Middleware | Cross-cutting concerns | Hold business state |

❌ **Passing raw `req`/`res` into services**

---

## 5. Input Validation (CRITICAL)

- Validate **every** input at the boundary with a schema (Zod / Joi / Pydantic / equivalent)
- Validate types, required fields, ranges, allowed values, and length limits
- Reject unknown/extra fields where appropriate
- **Never trust client input** — re-validate even if the frontend validated

```ts
// ✅ Validate before doing anything else
const dto = CreateUserSchema.parse(req.body);
const user = await userService.create(dto);
```

---

## 6. Error Handling (MANDATORY)

- Use **typed/domain errors** (e.g. `NotFoundError`, `ValidationError`, `ConflictError`) — not bare strings
- A **single centralized error handler** maps errors → HTTP responses (per `api_rules/API_STANDARDS.md`)
- Internal errors **must not leak stack traces** to clients
- No empty catch blocks; always log or rethrow with context

```ts
// ❌ BAD
try { await doWork(); } catch (e) {}

// ✅ GOOD
try {
  await doWork();
} catch (err) {
  logger.error('doWork failed', { err, requestId });
  throw new InternalError('Operation failed', { cause: err });
}
```

---

## 7. Configuration & Secrets

- All config loaded from **environment variables** via a single typed config module
- Validate config at startup; **fail fast** if required vars are missing
- Separate config per environment: `dev` / `staging` / `production`

❌ **Secrets hardcoded in code**
❌ **`.env` files committed to the repo**
❌ **Reading `process.env` scattered across the codebase**

---

## 8. Security Standards (CRITICAL)

- Enforce authentication & authorization **server-side** on every protected route (RBAC + object-level checks)
- Hash passwords with **bcrypt/argon2**; never store plaintext
- Parameterized queries / ORM only — **no string-concatenated SQL**
- Rate limit sensitive endpoints (auth, payments)
- Never log secrets, tokens, passwords, or PII
- Keep dependencies patched; run vulnerability scans in CI

> See `api_rules/SECURITY_HARDENING.md` and `api_rules/AUTH_STANDARDS.md` for full requirements.

---

## 9. Database & Transactions

- All DB access through the repository layer
- Use **transactions** for multi-step writes that must be atomic
- No N+1 queries; index query paths; paginate list reads
- Migrations are versioned and reversible

> Full rules in `database_rules/DATABASE_STANDARDS.md`.

❌ **Partial writes without transactions**
❌ **Long-running transactions holding locks**

---

## 10. Asynchronous & Background Work

- Offload heavy/slow tasks (email, image processing, exports) to **queues/workers**
- Jobs must be **idempotent** and safe to retry
- Use exponential backoff + dead-letter queues for failures
- Set timeouts on all external calls

❌ **Blocking the request thread on long-running work**
❌ **Fire-and-forget jobs with no retry/failure handling**

---

## 11. External Integrations

- Wrap third-party APIs behind a typed client in `lib/`
- Always set **timeouts**, **retries** (idempotent calls only), and **circuit breakers** for critical deps
- Handle and translate upstream errors into domain errors

---

## 12. Concurrency & Reliability

- Services must be **stateless** and horizontally scalable
- Guard against race conditions (locks, atomic ops, idempotency keys)
- Graceful shutdown: drain in-flight requests, close DB/queue connections
- Health checks: `/health` (liveness) and `/ready` (readiness)

---

## 13. Logging & Observability

Logs must be **structured (JSON)** and include:
- `requestId` / `traceId`
- `userId` (if available)
- `operation`
- `durationMs`
- `level`

Also required:
- Metrics (latency, error rate, throughput)
- Distributed tracing on critical paths
- Alerts on error-rate and latency SLO breaches

❌ **`console.log` debugging left in production code**
❌ **Sensitive data in logs**

---

## 14. Performance Standards

- Respect API SLAs (P95 < 1.5s) — see `api_rules/PERFORMANCE_SLA.md`
- Cache expensive reads (Redis) with explicit invalidation
- Use connection pooling for DB and external clients
- Paginate and stream large datasets

---

## 15. Testing Standards

**Mandatory:**
- Unit tests for services/business logic (mock repositories)
- Integration tests for repositories and API routes (real/test DB)
- Contract tests for external integrations

❌ **Testing only the happy path**
❌ **No tests at all**

---

## 16. Code Quality Rules

- Linter + formatter enforced in CI (ESLint/Prettier, Ruff/Black, etc.)
- Strict typing (TypeScript `strict`, Python type hints, etc.) — no `any`
- No dead code, no commented-out blocks
- Clear naming; functions do one thing

---

## 17. AI Agent Backend Rules

**AI MUST:**
- Respect layered architecture and dependency direction
- Validate all inputs at the boundary
- Use typed errors + centralized error handling
- Load config/secrets from env (never hardcode)
- Enforce auth server-side and never log secrets
- Make background work idempotent and retry-safe

**If unsure → STOP and ask.**

---

## 18. Pre-Merge Checklist

- ✅ Layering respected (controller → service → repository)
- ✅ All inputs validated at the boundary
- ✅ Typed errors + centralized handler
- ✅ Auth/RBAC enforced server-side
- ✅ No secrets in code; config validated at startup
- ✅ Transactions for atomic writes
- ✅ Structured logging, no sensitive data
- ✅ Tests added (unit + integration)

---

## 19. Forbidden Practices

- Business logic in controllers
- DB queries outside repositories
- String-concatenated SQL
- Hardcoded secrets / committed `.env`
- Empty catch blocks / silent failures
- Stateful, non-scalable services
- Blocking requests on heavy work

---

## 20. Final Statement

This backend standard ensures:
- Clean, maintainable, layered services
- Secure, validated, and observable systems
- Reliable, scalable, retry-safe operations
- Production-ready, enterprise-grade backends
