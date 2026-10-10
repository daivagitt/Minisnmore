# Indexing & Query Performance Standards

## Response Time Alignment
- Queries backing API endpoints must support **P95 < 1.5s** (see API Performance SLA)
- All list/filter endpoints must use indexed columns in `WHERE` and `ORDER BY`

---

## Mandatory Practices

- Index all foreign key columns
- Composite indexes for common filter combinations (document in migration comment)
- Use `EXPLAIN ANALYZE` for queries exceeding 100ms in development
- Pagination required — no unbounded `LIMIT`-less scans on large tables

---

## Forbidden

- N+1 query patterns (use joins or batch fetch)
- Full table scans on tables > 10k rows without justification
- Functions on indexed columns in `WHERE` (prevents index use)

---

## Monitoring

- Log slow queries above threshold in non-production
- Review index usage periodically; remove unused indexes
