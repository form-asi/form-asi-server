package com.formasiserver.domain.auth.jwt;

import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = readSigningKey(properties.secretKey());
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .build();
    }

    public IssuedToken issueAccessToken(UUID userId) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return issue(userId, TokenType.ACCESS, issuedAt, issuedAt.plus(properties.accessTtl()));
    }

    public IssuedToken issueRefreshToken(UUID userId) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return issue(userId, TokenType.REFRESH, issuedAt, issuedAt.plus(properties.refreshTtl()));
    }

    public IssuedToken issueRefreshToken(UUID userId, Instant loginExpiresAt) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = Objects.requireNonNull(loginExpiresAt, "Login expiry is required")
                .truncatedTo(ChronoUnit.SECONDS);
        if (!expiresAt.isAfter(issuedAt)) {
            throw unauthorized();
        }
        Assert.isTrue(!expiresAt.isAfter(issuedAt.plus(JwtProperties.MAX_REFRESH_TTL)),
                "Login expiry exceeds the maximum Refresh Token lifetime");
        return issue(userId, TokenType.REFRESH, issuedAt, expiresAt);
    }

    public VerifiedToken verifyAccessToken(String token) {
        return verify(token, TokenType.ACCESS);
    }

    public VerifiedToken verifyRefreshToken(String token) {
        return verify(token, TokenType.REFRESH);
    }

    private IssuedToken issue(UUID userId, TokenType type, Instant issuedAt, Instant expiresAt) {
        Assert.isTrue(issuedAt.isBefore(expiresAt), "Token expiry must be after issuance");
        String token = Jwts.builder()
                .header().type("JWT").and()
                .subject(Objects.requireNonNull(userId, "User ID is required").toString())
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, type.claimValue)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    private VerifiedToken verify(String token, TokenType expectedType) {
        if (!StringUtils.hasText(token)) {
            throw unauthorized();
        }

        try {
            Jws<Claims> signed = parser.parseSignedClaims(token);
            Claims claims = signed.getPayload();
            String headerType = signed.getHeader().getType();
            if ((headerType != null && !"JWT".equalsIgnoreCase(headerType))
                    || !expectedType.claimValue.equals(claims.get(TOKEN_TYPE_CLAIM))) {
                throw unauthorized();
            }

            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            Instant now = Instant.now();
            if (issuedAt == null || expiresAt == null || issuedAt.toInstant().isAfter(now)
                    || !now.isBefore(expiresAt.toInstant())) {
                throw unauthorized();
            }

            return new VerifiedToken(requireUuid(claims.get(Claims.SUBJECT)), requireUuid(claims.get(Claims.ID)),
                    issuedAt.toInstant(), expiresAt.toInstant());
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private static UUID requireUuid(Object claim) {
        if (!(claim instanceof String text)) {
            throw unauthorized();
        }
        UUID id;
        try {
            id = UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
        if (!id.toString().equalsIgnoreCase(text)) {
            throw unauthorized();
        }
        return id;
    }

    private static SecretKey readSigningKey(String value) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET_KEY must be Base64-encoded");
        }
        Assert.isTrue(bytes.length >= 32, "JWT_SECRET_KEY must decode to at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static ApiException unauthorized() {
        return new ApiException(ErrorCode.UNAUTHORIZED);
    }

    public record IssuedToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[expiresAt=" + expiresAt + "]";
        }
    }

    public record VerifiedToken(UUID userId, UUID tokenId, Instant issuedAt, Instant expiresAt) {
    }

    @RequiredArgsConstructor
    private enum TokenType {
        ACCESS("access"),
        REFRESH("refresh");

        private final String claimValue;
    }
}
