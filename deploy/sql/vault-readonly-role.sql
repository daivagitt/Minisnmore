-- Optional: read-only Postgres role for gateway/store/commerce VAULT_JDBC_* credentials.
-- Run as a superuser on the catalog database (miniandmore).
--
-- Replace passwords and role names for your environment.

-- CREATE ROLE vault_reader LOGIN PASSWORD 'change-me';
-- GRANT CONNECT ON DATABASE miniandmore TO vault_reader;
-- GRANT USAGE ON SCHEMA public TO vault_reader;
-- GRANT SELECT ON TABLE app_vault_secret TO vault_reader;

-- Then in prod.env for gateway/store/commerce:
-- VAULT_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/miniandmore
-- VAULT_JDBC_USERNAME=vault_reader
-- VAULT_JDBC_PASSWORD=change-me
