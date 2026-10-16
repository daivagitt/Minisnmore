# Gateway rate limiting

The gateway applies Redis-backed, atomic fixed-window limits before authentication,
covering its local login controllers and proxied API routes. All instances in a stack
must use the same Redis database and key prefix. Different stacks should use distinct
prefixes. Redis keys expire automatically. Rejected requests do not extend the window.

Default limits, per client IP:

| Policy | Requests per 60 seconds | Coverage |
| --- | ---: | --- |
| General | 600 | API traffic, including specialized endpoints except webhooks |
| Authentication | 5 | Login, registration, password reset, activation resend, invite completion, OAuth start/callback |
| Order creation | 5 | POST /api/v1/orders |
| Payment | 30 | Payment sync, verification and retry, combined |
| Admin | 120 | /api/v1/admin/** |
| Webhook | 300 | Razorpay, Xpressbees, Zoho and Ledgerly webhook routes, independently of general traffic |

The payment allowance accommodates one checkout polling every three seconds, with
room for verification. Users behind the same public IP share these limits. Multiple
simultaneous checkouts behind a shared IP may need a higher payment allowance.
Limits do not provide payment idempotency, account lockout protection, job concurrency
control or a global server capacity guarantee. A fixed window can allow two full
allowances across its boundary. Tune these starting values using representative load
tests and observed 429 rates.

## Configuration

The source of truth is `application.rate-limiting` in the gateway's `application.yml`.
The obsolete development-only JHipster limiter settings have been removed.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| GATEWAY_RATE_LIMIT_ENABLED | true | Enable limits |
| GATEWAY_RATE_LIMIT_REDIS_URL | redis://127.0.0.1:6379 | Shared Redis, supports credentials and rediss TLS URLs |
| GATEWAY_RATE_LIMIT_KEY_PREFIX | gateway:rate-limit: | Namespace shared by instances of one stack |
| GATEWAY_RATE_LIMIT_FAIL_OPEN | false | If true, permit requests when Redis fails |
| GATEWAY_RATE_LIMIT_TRUSTED_PROXIES | loopback IPv4/IPv6 | Comma-separated exact transport peer IPs allowed to supply X-Real-IP |
| GATEWAY_RATE_LIMIT_{POLICY}_REQUESTS | See table | Positive request allowance |
| GATEWAY_RATE_LIMIT_{POLICY}_WINDOW_SECONDS | 60 | Positive window duration |

`{POLICY}` is GENERAL, AUTH, ORDERS, PAYMENT, ADMIN or WEBHOOK. For example,
`GATEWAY_RATE_LIMIT_PAYMENT_REQUESTS=60` permits 60 payment operations per window.
Redis operations time out after 500 ms. The default fail-closed policy returns 503
with `Retry-After: 1` during Redis failures; it does not forward the request. Fail-open
preserves availability but disables protection while Redis is unavailable.

## Proxy and deployment requirements

The limiter obtains the original TCP peer even when Reactor Netty's native forwarded
header support rewrites the request address. It accepts a numeric `X-Real-IP` only
from explicitly trusted peers and ignores `X-Forwarded-For` for limit keys. Nginx must
overwrite `X-Real-IP` with the verified client address. When Cloudflare is in front,
configure Nginx real-IP handling using only trusted Cloudflare proxy ranges before
using these limits; otherwise allowances may be shared by Cloudflare edge IPs.
Do not trust arbitrary proxy addresses or expose downstream API ports publicly.

Before rolling out:

1. Provision Redis and set `GATEWAY_RATE_LIMIT_REDIS_URL` on all gateway instances (see
   `deploy/env/prod.env.example` — this is **not** `JHIPSTER_CACHE_REDIS_SERVER`). Use a
   distinct `GATEWAY_RATE_LIMIT_KEY_PREFIX` for UAT and production. `gateway_verify_env`
   pings Redis when rate limiting is enabled.
2. Build and deploy the gateway through the normal deployment process.
3. Apply the Nginx template changes routing both invoice webhook aliases through port
   9180, then validate and reload Nginx. Other deployments must likewise route public
   APIs and webhooks through their gateway. Live Nginx and services were not changed
   as part of this source implementation.
4. Check a non-production login or API route: exceed its configured allowance and
   verify 429 plus `Retry-After`, then verify access resumes after expiry. Avoid using
   real checkout submissions for this smoke test. Check payment polling and webhook
   deliveries under expected concurrency.

Preflight OPTIONS requests, static assets and management health endpoints bypass the
limiter. Rejections contain a JSON problem response, `Cache-Control: no-store` and
`Retry-After` in seconds. The existing frontend already handles 429 with a retry message.
The filter applies CORS headers to error responses for allowed origins.

Monitor Micrometer counters `gateway.rate.limit.rejections` (policy and reason tags)
and `gateway.rate.limit.errors`. A Redis outage should trigger an alert.

## Validation

Run from `services/gateway`:

```sh
./mvnw -Dtest=ApiRateLimitWebFilterTest,RedisRequestLimiterTest,RateLimitingConfigurationTest,ApplicationPropertiesTest,OAuthProviderGateWebFilterTest test
```

The Redis tests start a temporary isolated `redis-server` on a random local port.
They skip when the executable is unavailable. They exercise concurrent requests from
two limiter instances, expiration and client isolation against the actual Lua script.
Filter tests cover classification, rejection headers, general/specialized budgets,
Redis failures, downstream errors, bypasses and forwarded-header spoofing, including
a real Netty server with native forwarding enabled.
