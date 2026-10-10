# API Performance SLA

## Response Time Targets
- P95 < 1.5 seconds
- P99 < 2 seconds

---

## Mandatory Optimizations
- Pagination for all list endpoints
- Indexed database queries
- No N+1 query patterns
- Async processing for heavy tasks

---

## Monitoring
- Latency tracking
- Error rate alerts
- Health check endpoint required
