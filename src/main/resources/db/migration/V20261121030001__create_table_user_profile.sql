CREATE TABLE user_profile (
    user_id UUID PRIMARY KEY REFERENCES users (user_id),
    gender VARCHAR(20) NOT NULL,
    job VARCHAR(40) NOT NULL,
    job_etc VARCHAR(20),
    region VARCHAR(20),
    household_type VARCHAR(32),
    household_etc VARCHAR(20),
    household_size VARCHAR(20),
    marital_status VARCHAR(20),
    marital_status_etc VARCHAR(20),
    children VARCHAR(20),
    pet_experience VARCHAR(20),
    driver_license VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_profile_job_etc CHECK (
        CASE WHEN job = 'OTHER'
            THEN job_etc IS NOT NULL AND job_etc ~ '[^[:space:]]'
            ELSE job_etc IS NULL
        END
    ),
    CONSTRAINT ck_user_profile_household_etc CHECK (
        CASE WHEN household_type = 'OTHER'
            THEN household_etc IS NOT NULL AND household_etc ~ '[^[:space:]]'
            ELSE household_etc IS NULL
        END
    ),
    CONSTRAINT ck_user_profile_marital_etc CHECK (
        CASE WHEN marital_status = 'OTHER'
            THEN marital_status_etc IS NOT NULL AND marital_status_etc ~ '[^[:space:]]'
            ELSE marital_status_etc IS NULL
        END
    )
);
