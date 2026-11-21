package com.formasiserver.domain.auth.security;

import org.jspecify.annotations.Nullable;

public final class BearerTokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenResolver() {
    }

    public static @Nullable String resolve(@Nullable String authorization) {
        if (authorization == null || !authorization.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
