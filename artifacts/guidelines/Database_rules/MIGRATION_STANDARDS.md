# Migration Standards (Liquibase)

## Tooling
- **Liquibase** for all schema changes
- Changelog files under `src/main/resources/config/liquibase/changelog/`
- One logical change per changeset

---

## Rules

- Every changeset must have a unique `id` and `author`
- Include rollback instructions where Liquibase supports it
- Never edit a changeset that has already run in production — add a new one
- Test migrations against a copy of production-like data before deploy

---

## Naming

- Changelog files: `YYYYMMDDHHMMSS_description.xml` or project convention
- Changeset id: descriptive, e.g. `20260609-add-trader-status-index`

---

## Forbidden

- Direct DDL in application startup (except Liquibase)
- Dropping columns/tables without deprecation period
- Data migrations without backup plan
