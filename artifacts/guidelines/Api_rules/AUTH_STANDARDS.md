# Authentication & Authorization Standards

## Authentication
- JWT access tokens (short-lived)
- Refresh tokens (secure storage)
- RS256 signing preferred
- Token expiration mandatory

---

## Authorization
- Role-Based Access Control (RBAC)
- Object-level access checks
- Admin routes enforced server-side

---

## Token Rules
- Access token expiry ≤ 15 minutes
- Refresh token rotation required
- Token revocation supported

---

## Password Rules
- bcrypt or argon2 hashing
- Minimum 12 characters
- Rate-limited login attempts

---

## Forbidden
- Auth in query params
- Long-lived access tokens
- Client-side trust
