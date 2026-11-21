package com.formasiserver.domain.auth.security;

import com.formasiserver.domain.auth.jwt.JwtProvider.IssuedToken;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;

    public RefreshTokenCookieFactory(@Value("${auth.refresh-cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(IssuedToken refresh) {
        long remainingSeconds = refresh.expiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        return cookie(refresh.value(), Math.max(0, remainingSeconds));
    }

    public ResponseCookie clear() {
        return cookie("", 0);
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(NAME, value)
                .path(PATH)
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .maxAge(maxAge)
                .build();
    }
}
