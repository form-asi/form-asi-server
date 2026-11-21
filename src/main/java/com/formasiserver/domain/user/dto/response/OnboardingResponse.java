package com.formasiserver.domain.user.dto.response;

import com.formasiserver.domain.user.entity.UserStatus;
import java.util.UUID;

public record OnboardingResponse(UUID userId, String nickname, UserStatus status) {
}
