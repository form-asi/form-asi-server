package com.formasiserver.domain.auth.jwt;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        @NotBlank String secretKey,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl
) {

    public static final Duration MAX_REFRESH_TTL = Duration.ofDays(14);

    @AssertTrue(message = "JWT lifetimes must be positive whole seconds")
    public boolean isLifetimeValid() {
        return isPositiveWholeSeconds(accessTtl) && isPositiveWholeSeconds(refreshTtl);
    }

    @AssertTrue(message = "Refresh Token lifetime must not exceed 14 days")
    public boolean isRefreshTtlWithinLimit() {
        return refreshTtl == null || refreshTtl.compareTo(MAX_REFRESH_TTL) <= 0;
    }

    private static boolean isPositiveWholeSeconds(Duration ttl) {
        return ttl != null && ttl.getSeconds() > 0 && ttl.getNano() == 0;
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=%s, audience=%s, accessTtl=%s, refreshTtl=%s]"
                .formatted(issuer, audience, accessTtl, refreshTtl);
    }
}
