# Database Naming Conventions

## Tables
- Plural, `snake_case`: `traders`, `auction_sessions`, `voucher_lines`
- Junction tables: `entity_a_entity_b` alphabetically ordered

## Columns
- `snake_case`: `first_name`, `created_at`, `trader_id`
- Booleans: `is_active`, `has_settled` (positive naming preferred)
- Foreign keys: `{referenced_table_singular}_id` e.g. `trader_id`

## Indexes
- `idx_{table}_{columns}`: `idx_traders_email`
- Unique: `uk_{table}_{columns}`

## Constraints
- Primary key: `pk_{table}`
- Foreign key: `fk_{table}_{referenced_table}`

## Liquibase
- Changelog context must match environment naming in CI
