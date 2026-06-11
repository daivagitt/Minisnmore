-- Atomic user ids for gateway R2DBC (replaces race-prone MAX(id)+1 under concurrent sign-up / Google login).
CREATE SEQUENCE IF NOT EXISTS jhi_user_id_seq;

SELECT setval(
    'jhi_user_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM jhi_user), 1)
);
