ALTER TABLE time_transfer
    ADD COLUMN IF NOT EXISTS survey_id BIGINT REFERENCES survey (id);
