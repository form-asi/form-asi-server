ALTER TABLE users
    ALTER COLUMN birth_date DROP NOT NULL,
    ADD CONSTRAINT ck_users_active_birth_date CHECK (status <> 'ACTIVE' OR birth_date IS NOT NULL);
