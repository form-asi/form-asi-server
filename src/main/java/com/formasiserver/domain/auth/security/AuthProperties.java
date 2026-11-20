package com.formasiserver.domain.auth.security;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("auth")
public record AuthProperties(List<String> allowedOrigins) {

    public AuthProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            Assert.isTrue(("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                            && uri.getHost() != null && uri.getRawUserInfo() == null && uri.getPort() <= 65535
                            && uri.getRawPath().isEmpty() && uri.getRawQuery() == null && uri.getRawFragment() == null,
                    "AUTH_ALLOWED_ORIGINS must contain exact HTTP(S) origins");
        }
    }
}
