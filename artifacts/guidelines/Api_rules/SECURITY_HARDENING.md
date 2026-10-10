# Security Hardening Standards

## Network
- HTTPS only
- TLS 1.2+
- HSTS enabled
- Content-Security-Policy (CSP) with default-src and frame-ancestors directives required
- X-Frame-Options or frame-ancestors CSP directive required to prevent clickjacking
- X-Content-Type-Options: nosniff required to prevent MIME sniffing
- Referrer-Policy (e.g., no-referrer or strict-origin-when-cross-origin) required to limit referrer leakage

---

## API Protection
- Rate limiting enabled
- Request size limits enforced
- Input validation mandatory

---

## Data Protection
- Secrets in environment variables only
- No sensitive data in logs
- Encryption at rest where applicable

---

## CORS
- Explicit allowlist
- No wildcard origins in production

---

## Incident Prevention
- Brute force protection
- Abuse monitoring
- Audit logs for sensitive actions
