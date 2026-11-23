package com.formasiserver.domain.helptime.dto.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record HelpTimeTransactionResponse(
        Instant createdAt,
        String reason,
        @Nullable String surveyTitle,
        long changeMinutes,
        int balanceAfter,
        @Nullable Instant expiresAt
) {

    public HelpTimeTransactionResponse(Instant createdAt, String reason, long changeMinutes, int balanceAfter,
            @Nullable Instant expiresAt) {
        this(createdAt, reason, null, changeMinutes, balanceAfter, expiresAt);
    }
}
