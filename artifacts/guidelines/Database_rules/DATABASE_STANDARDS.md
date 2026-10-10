# Database Standards

## Mandatory Reading
This file MUST be read and followed by any AI agent or developer before writing schema, queries, or migrations.

If there is a conflict between speed and data integrity:
→ Integrity, security, and correctness ALWAYS win.

---

## Core Principles (Non-Negotiable)

- **PostgreSQL as the system of record**
- **Migrations are version-controlled and reversible**
- **No destructive changes without explicit approval**
- **Indexed access paths for all hot queries**
- **Least-privilege database roles**
- **No sensitive data in plain text**

---

## Schema Design

- Use `snake_case` for tables and columns
- Primary keys: `BIGSERIAL` or `UUID` (project-standard per entity)
- Every table must have `created_at` and `updated_at` timestamps where applicable
- Foreign keys must be enforced at the database level
- Soft deletes via `deleted_at` where business requires audit trail

---

## Forbidden

- Raw string concatenation in SQL
- `SELECT *` in production service code
- Unbounded `DELETE` or `UPDATE` without `WHERE`
- Schema changes applied manually in production
- Shared DB credentials across environments

---

## Output Expectations

- Follow MIGRATION_STANDARDS.md strictly
- Follow INDEXING_STANDARDS.md for query performance
- Follow NAMING_CONVENTIONS.md for consistency

Failure to comply = rejected output
