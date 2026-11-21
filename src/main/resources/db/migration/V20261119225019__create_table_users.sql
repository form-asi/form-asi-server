CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    kakao_id VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    nickname VARCHAR(10),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_kakao_id UNIQUE (kakao_id)
);