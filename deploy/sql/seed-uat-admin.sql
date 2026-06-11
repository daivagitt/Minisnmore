-- Run on the UAT PostgreSQL database (as a user with write access to jhi_* tables).
-- Creates/updates admin: admin@uat.minisnmore.com / password admin6 (min 6 characters)

INSERT INTO jhi_user (
    id, login, password_hash, first_name, last_name, email, activated, lang_key, created_by, last_modified_by, created_date
)
SELECT
    nextval('sequence_generator'),
    'admin',
    '$2b$10$qICqJgquatiW1PT3D9hRv.1eV/BJjkPMHD164zGMmJ34xCDwbqBu.',
    'Administrator',
    'Administrator',
    'admin@uat.minisnmore.com',
    true,
    'en',
    'system',
    'system',
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM jhi_user WHERE login = 'admin');

UPDATE jhi_user
SET
    password_hash = '$2b$10$qICqJgquatiW1PT3D9hRv.1eV/BJjkPMHD164zGMmJ34xCDwbqBu.',
    email = 'admin@uat.minisnmore.com',
    activated = true
WHERE login = 'admin';

INSERT INTO jhi_user_authority (user_id, authority_name)
SELECT u.id, 'ROLE_ADMIN'
FROM jhi_user u
WHERE u.login = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM jhi_user_authority ua WHERE ua.user_id = u.id AND ua.authority_name = 'ROLE_ADMIN'
  );

INSERT INTO jhi_user_authority (user_id, authority_name)
SELECT u.id, 'ROLE_USER'
FROM jhi_user u
WHERE u.login = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM jhi_user_authority ua WHERE ua.user_id = u.id AND ua.authority_name = 'ROLE_USER'
  );
