package com.formasiserver.domain.auth.security;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth")
public record AuthProperties(List<String> allowedOrigins, URI loginRedirectUrl) {

    public AuthProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            if ((!"http".equals(scheme) && !"https".equals(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() > 65535
                    || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("AUTH_ALLOWED_ORIGINS must contain exact HTTP(S) origins");
            }
        }

        if (loginRedirectUrl == null) {
            throw new IllegalArgumentException("AUTH_LOGIN_REDIRECT_URL must be configured");
        }
        String scheme = loginRedirectUrl.getScheme();
        if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || loginRedirectUrl.getHost() == null || loginRedirectUrl.getRawUserInfo() != null
                || loginRedirectUrl.getPort() > 65535) {
            throw new IllegalArgumentException("AUTH_LOGIN_REDIRECT_URL must be an absolute HTTP(S) URL");
        }
    }
}
