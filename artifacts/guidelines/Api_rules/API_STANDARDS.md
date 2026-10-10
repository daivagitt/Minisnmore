## API_STANDARDS.md

### 1. Purpose of This Document

This document defines **mandatory standards** for designing, implementing, and maintaining APIs in this project.

⚠️ Any API implementation that violates this document is considered **non-production-ready**.

All AI agents and developers **must read and follow this file before implementation**.

---

### 2. Core Principles (Non-Negotiable)

- **Security by default**
- **Explicit authentication & authorization**
- **Predictable responses**
- **< 1.5s response time (P95)**
- **Zero trust architecture**
- **Fail safely, never silently**
- **Production-grade logging & monitoring**

---

### 3. API Architecture Standard

#### 3.1 Protocol

- **REST over HTTPS only**
- **HTTP/1.1 or HTTP/2**
- **JSON only** (`application/json`)
- **UTF-8** encoding

❌ No GraphQL  
❌ No SOAP  
❌ No plain HTTP

---

### 4. Authentication & Authorization (MANDATORY)

#### 4.1 Authentication Mechanism

- **JWT-based authentication**
- Tokens **must be signed** (RS256 preferred)
- Tokens **must expire**
- Refresh tokens stored securely (**HTTP-only**)

#### 4.2 Required Headers

- `Authorization: Bearer <access_token>`
- `Content-Type: application/json`

❌ No auth via query params  
❌ No long-lived access tokens

#### 4.3 Authorization Rules

- Every request **must validate user identity**
- **Role-based access control (RBAC)**
- **Object-level permission checks**

**Example rule:**
- User can only access **their own resources**
- **Admin-only** routes must be enforced **server-side**

---

### 5. API Versioning

- **Versioning is mandatory**
- Version **must be in the URL**, for example:

`/api/v1/users`

**Rules:**
- Never break backward compatibility **within a version**
- Major breaking changes → **new version**

---

### 6. Request Standards

#### 6.1 Request Validation

Every request **must be validated** for:

- Required fields
- Data type correctness
- Length limits
- Allowed values
- Malformed JSON

❌ **Never trust client input**

#### 6.2 Payload Size Limits

- Max request body: **1MB**
- File uploads: **pre-signed URLs only**

---

### 7. Response Standards

#### 7.1 Standard Response Structure

✅ **Success Response**

```json
{
  "success": true,
  "data": {},
  "meta": {
    "requestId": "uuid",
    "timestamp": "ISO-8601"
  }
}
```

❌ **Error Response**

```json
{
  "success": false,
  "error": {
    "code": "INVALID_INPUT",
    "message": "Readable error message"
  },
  "meta": {
    "requestId": "uuid",
    "timestamp": "ISO-8601"
  }
}
```

#### 7.2 HTTP Status Codes

| Code | Usage                 |
|------|-----------------------|
| 200  | Success               |
| 201  | Resource created      |
| 400  | Validation error      |
| 401  | Unauthorized          |
| 403  | Forbidden             |
| 404  | Not found             |
| 409  | Conflict              |
| 429  | Rate limit exceeded   |
| 500  | Internal server error |

❌ **Never return 200 for errors**

---

### 8. Performance Standards

#### 8.1 Response Time

- **P95 < 1.5 seconds**
- **P99 < 2 seconds**

#### 8.2 Performance Rules

- DB queries **must be indexed**
- **No N+1 queries**
- **Pagination mandatory** for list endpoints
- **Async processing** for heavy tasks

---

### 9. Security Standards (CRITICAL)

#### 9.1 Transport Security

- **HTTPS only**
- **TLS 1.2+**
- **HSTS enabled**

#### 9.2 Input Security

- SQL Injection protection
- No raw queries without sanitization
- Strict schema validation

#### 9.3 Rate Limiting

- Per-IP & per-user limits

**Example:**
- 100 requests/minute per user  
- 20 requests/minute for auth endpoints

#### 9.4 Sensitive Data Handling

- ❌ Never log tokens, passwords, secrets
- Passwords must be hashed (**bcrypt/argon2**)
- Secrets stored in **environment variables only**

#### 9.5 CORS Policy

- Explicit allowlist
- **No wildcard origins in production**

---

### 10. Error Handling & Logging

#### 10.1 Logging Rules

Logs must be **structured (JSON)** and include:

- `requestId`
- `userId` (if available)
- `endpoint`
- `statusCode`
- `executionTime`

❌ No sensitive data in logs

#### 10.2 Error Exposure

- Internal errors must **not leak stack traces** to the client
- Client sees **friendly, generic error messages only**

---

### 11. Monitoring & Observability

Mandatory:

- Health check endpoint (`/health`)
- Uptime monitoring
- Error rate monitoring
- Latency tracking

---

### 12. Pagination & Filtering

**Example:**

`GET /api/v1/posts?page=1&limit=20`

**Rules:**

- Default `limit`: **20**
- Max `limit`: **50**

---

### 13. Idempotency

- Required for **POST/PUT** where duplication is risky
- Use `Idempotency-Key` header

---

### 14. Documentation Requirements

- **OpenAPI (Swagger) spec required**
- Examples for **every endpoint**
- Auth requirements **must be documented**

---

### 15. AI Agent Enforcement Rule

Any AI agent implementing APIs **must strictly follow this document**.

If a generated API:

- skips auth  
- ignores validation  
- lacks rate limiting  
- exposes sensitive data  

➡️ It **must be rejected immediately**.

---

### 16. Production Readiness Checklist

Before deployment:

- ✅ Auth enforced  
- ✅ RBAC verified  
- ✅ Rate limiting enabled  
- ✅ Logs structured  
- ✅ Monitoring live  
- ✅ Security reviewed  

---

### 17. Final Statement

This API standard is designed to be:

- **Secure**
- **Scalable**
- **Auditable**
- **Production-ready**
- **Enterprise-grade**
