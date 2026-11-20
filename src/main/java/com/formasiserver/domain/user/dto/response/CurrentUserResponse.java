package com.formasiserver.domain.user.dto.response;

import com.formasiserver.domain.user.entity.UserStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CurrentUserResponse(UUID userId, @Nullable String nickname, int age, UserStatus status) {
}
