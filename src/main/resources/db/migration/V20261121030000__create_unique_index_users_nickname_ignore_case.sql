CREATE UNIQUE INDEX uk_users_nickname_ignore_case ON users (lower(nickname COLLATE "C"));
