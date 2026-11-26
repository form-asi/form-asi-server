package com.formasiserver.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingsUpdateRequest(
        @NotNull(message = "알림을 받을지 선택해주세요.") Boolean notificationEnabled
) {
}
