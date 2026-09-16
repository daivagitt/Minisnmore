-- Safe UAT schema patch: additive only — keeps user_id for the running monolith.
-- Run once on the shared miniandmore DB before microservices can serve site-settings / orders.
-- Idempotent: safe to re-run.

-- 1) Site settings — subscribe page columns (microservices SiteSetting entity)
ALTER TABLE site_setting ADD COLUMN IF NOT EXISTS subscribe_hero_title varchar(260);
ALTER TABLE site_setting ADD COLUMN IF NOT EXISTS subscribe_hero_subtitle varchar(600);
ALTER TABLE site_setting ADD COLUMN IF NOT EXISTS subscribe_testimonial varchar(600);
ALTER TABLE site_setting ADD COLUMN IF NOT EXISTS subscribe_badges_json text;
ALTER TABLE site_setting ADD COLUMN IF NOT EXISTS subscribe_plans_json text;
ALTER TABLE site_setting ADD COLUMN IF NOT EXISTS subscribe_features_json text;

-- 2) customer_profile — add user_login alongside user_id (do NOT drop user_id)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'customer_profile' AND column_name = 'user_id'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'customer_profile' AND column_name = 'user_login'
  ) THEN
    ALTER TABLE customer_profile ADD COLUMN user_login varchar(50);
    UPDATE customer_profile cp
    SET user_login = LOWER(u.login)
    FROM jhi_user u
    WHERE cp.user_id = u.id AND cp.user_login IS NULL;
    UPDATE customer_profile SET user_login = 'unknown-' || id::text WHERE user_login IS NULL;
    ALTER TABLE customer_profile ALTER COLUMN user_login SET NOT NULL;
    CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_profile__user_login ON customer_profile (user_login);
  END IF;
END $$;

-- 3) customer_order — add user_login alongside user_id (do NOT drop user_id)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'customer_order' AND column_name = 'user_id'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'customer_order' AND column_name = 'user_login'
  ) THEN
    ALTER TABLE customer_order ADD COLUMN user_login varchar(50);
    UPDATE customer_order co
    SET user_login = LOWER(u.login)
    FROM jhi_user u
    WHERE co.user_id = u.id AND co.user_login IS NULL;
    UPDATE customer_order SET user_login = 'unknown-' || id::text WHERE user_login IS NULL;
    ALTER TABLE customer_order ALTER COLUMN user_login SET NOT NULL;
  END IF;
END $$;

-- 4) order_return — user_login if table exists without column
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_name = 'order_return'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'order_return' AND column_name = 'user_login'
  ) THEN
    ALTER TABLE order_return ADD COLUMN user_login varchar(50);
    UPDATE order_return SET user_login = 'unknown-' || id::text WHERE user_login IS NULL;
    ALTER TABLE order_return ALTER COLUMN user_login SET NOT NULL;
    CREATE INDEX IF NOT EXISTS idx_order_return__user_login ON order_return (user_login);
  END IF;
END $$;

-- 5) customer_order — columns added after monolith baseline (microservice entity expects these)
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS inventory_reserved boolean;
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS inventory_reserved_until timestamp;
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS inventory_released_at timestamp;
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_sales_order_id varchar(120);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_invoice_id varchar(120);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_sync_status varchar(20);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_last_synced_at timestamp;
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_sync_error varchar(1000);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_order_status varchar(40);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS tracking_number varchar(120);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS tracking_carrier varchar(120);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS shipment_date varchar(20);
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS zoho_tracking_last_pulled_at timestamp;

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_order__zoho_sales_order_id
  ON customer_order (zoho_sales_order_id) WHERE zoho_sales_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_customer_order__inventory_reservation
  ON customer_order (inventory_reserved, inventory_reserved_until, payment_status);
CREATE INDEX IF NOT EXISTS idx_customer_order__zoho_sync_status ON customer_order (zoho_sync_status);
CREATE INDEX IF NOT EXISTS idx_customer_order__zoho_tracking_last_pulled_at
  ON customer_order (zoho_tracking_last_pulled_at);

-- 6) Microservices insert user_login only; monolith schema may still require user_id NOT NULL.
-- Only install the sync trigger when user_id still exists. On fully migrated DBs
-- (user_login only) creating this trigger breaks inserts with:
--   ERROR: record "new" has no field "user_id"
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'customer_order' AND column_name = 'user_id'
  ) THEN
    ALTER TABLE customer_order ALTER COLUMN user_id DROP NOT NULL;

    CREATE OR REPLACE FUNCTION trg_customer_order_sync_user_id()
    RETURNS TRIGGER AS $fn$
    BEGIN
      IF NEW.user_id IS NULL AND NEW.user_login IS NOT NULL THEN
        SELECT u.id INTO NEW.user_id
        FROM jhi_user u
        WHERE LOWER(u.login) = LOWER(NEW.user_login)
        LIMIT 1;
      END IF;
      RETURN NEW;
    END;
    $fn$ LANGUAGE plpgsql;

    DROP TRIGGER IF EXISTS customer_order_sync_user_id ON customer_order;
    CREATE TRIGGER customer_order_sync_user_id
      BEFORE INSERT OR UPDATE ON customer_order
      FOR EACH ROW EXECUTE FUNCTION trg_customer_order_sync_user_id();
  ELSE
    -- Fully migrated: ensure a stale trigger from an older patch run cannot break inserts.
    DROP TRIGGER IF EXISTS customer_order_sync_user_id ON customer_order;
    DROP FUNCTION IF EXISTS trg_customer_order_sync_user_id();
  END IF;
END $$;
