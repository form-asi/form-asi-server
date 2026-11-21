package com.formasiserver.domain.auth.security;

import com.formasiserver.domain.user.entity.UserRole;
import com.formasiserver.domain.user.entity.UserStatus;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, UserRole role, UserStatus status) {
}
