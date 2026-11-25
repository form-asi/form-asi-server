ALTER TABLE time_account
    ADD COLUMN IF NOT EXISTS survey_id BIGINT REFERENCES survey (id)
        CONSTRAINT uk_time_account_survey_id UNIQUE,
    ALTER COLUMN user_id DROP NOT NULL,
    DROP CONSTRAINT IF EXISTS ck_time_account_owner_type,
    ADD CONSTRAINT ck_time_account_owner_type CHECK (
        (owner_type = 'USER' AND user_id IS NOT NULL AND survey_id IS NULL)
        OR (owner_type = 'SURVEY' AND survey_id IS NOT NULL AND user_id IS NULL)
    );
