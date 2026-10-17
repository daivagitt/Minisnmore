-- Production hotfix: order_return.user_id is a leftover monolith column.
-- Catalog writes user_login only, so POST /api/v1/order-returns fails with
-- "A required field was missing" (Postgres 23502 on user_id).
-- Mirrors liquibase 20261016180000_drop_order_return_legacy_user_id.xml
-- Idempotent: safe to re-run.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'order_return'
      AND column_name = 'user_id'
  ) THEN
    UPDATE order_return r
    SET user_login = LOWER(u.login)
    FROM jhi_user u
    WHERE r.user_id = u.id
      AND (r.user_login IS NULL OR BTRIM(r.user_login) = '');

    ALTER TABLE order_return DROP CONSTRAINT IF EXISTS fk_order_return__user_id;
    ALTER TABLE order_return DROP COLUMN user_id;
  END IF;
END $$;
