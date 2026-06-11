-- Fix legacy PostgreSQL OID/LOB columns on zoho_setting (Hibernate: "Large Objects may not be used in auto-commit mode").
-- Idempotent: only alters columns still stored as oid. Safe to re-run.
-- Run on UAT: psql -h 127.0.0.1 -U miniandmore_user -d miniandmore -f deploy/scripts/uat-zoho-setting-oid-to-varchar.sql

\set ON_ERROR_STOP on

-- Diagnose (informational)
SELECT column_name, data_type, udt_name, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'zoho_setting'
ORDER BY ordinal_position;

DO $$
DECLARE
  col text;
  v_len int;
  v_using text;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'zoho_setting'
  ) THEN
    RAISE NOTICE 'Table zoho_setting does not exist — skip';
    RETURN;
  END IF;

  FOR col, v_len IN
    SELECT c.column_name,
      CASE c.column_name
        WHEN 'org_id' THEN 120
        WHEN 'client_id' THEN 220
        WHEN 'client_secret' THEN 500
        WHEN 'refresh_token' THEN 800
        WHEN 'data_center' THEN 40
        ELSE 1000
      END
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.table_name = 'zoho_setting'
      AND c.udt_name = 'oid'
      AND c.column_name IN ('org_id', 'client_id', 'client_secret', 'refresh_token', 'data_center')
  LOOP
    v_using := format(
      'CASE WHEN %1$I IS NULL THEN NULL ELSE convert_from(lo_get(%1$I::oid), ''UTF8'') END',
      col
    );
    RAISE NOTICE 'Converting zoho_setting.% from oid to varchar(%)', col, v_len;
    EXECUTE format(
      'ALTER TABLE zoho_setting ALTER COLUMN %I TYPE varchar(%s) USING %s',
      col,
      v_len,
      v_using
    );
  END LOOP;
END $$;

-- If columns are already varchar/text but JDBC still maps as LOB, widen types (no-op if unchanged)
ALTER TABLE zoho_setting ALTER COLUMN org_id TYPE varchar(120);
ALTER TABLE zoho_setting ALTER COLUMN client_id TYPE varchar(220);
ALTER TABLE zoho_setting ALTER COLUMN client_secret TYPE varchar(500);
ALTER TABLE zoho_setting ALTER COLUMN refresh_token TYPE varchar(800);
ALTER TABLE zoho_setting ALTER COLUMN data_center TYPE varchar(40);

SELECT column_name, data_type, udt_name
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'zoho_setting'
  AND column_name IN ('org_id', 'client_id', 'client_secret', 'refresh_token', 'data_center')
ORDER BY ordinal_position;
