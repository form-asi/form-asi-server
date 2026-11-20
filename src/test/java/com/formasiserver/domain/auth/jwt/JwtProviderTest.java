package com.formasiserver.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtProviderTest {

    private static final byte[] TEST_KEY = "0123456789abcdef".repeat(4).getBytes(StandardCharsets.UTF_8);
    private static final String ENCODED_TEST_KEY = Base64.getEncoder().encodeToString(TEST_KEY);
    private static final String ISSUER = "form-asi-server";
    private static final String AUDIENCE = "form-asi-api";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TOKEN_ID = UUID.randomUUID();

    private final JwtProvider provider = new JwtProvider(properties(ENCODED_TEST_KEY));

    @Test
    void issuesAccessAndRefreshTokensWithTheirOwnLifetimes() {
        JwtProvider.IssuedToken access = provider.issueAccessToken(USER_ID);
        JwtProvider.IssuedToken refresh = provider.issueRefreshToken(USER_ID);
        JwtProvider.VerifiedToken verifiedAccess = provider.verifyAccessToken(access.value());
        JwtProvider.VerifiedToken verifiedRefresh = provider.verifyRefreshToken(refresh.value());

        assertThat(verifiedAccess.userId()).isEqualTo(USER_ID);
        assertThat(verifiedAccess.tokenId()).isNotEqualTo(verifiedRefresh.tokenId());
        assertThat(verifiedAccess.issuedAt().plus(ACCESS_TTL)).isEqualTo(access.expiresAt())
                .isEqualTo(verifiedAccess.expiresAt());
        assertThat(verifiedRefresh.issuedAt().plus(REFRESH_TTL)).isEqualTo(refresh.expiresAt())
                .isEqualTo(verifiedRefresh.expiresAt());
        assertThat(verifiedRefresh.userId()).isEqualTo(USER_ID);
        JwtParser parser = Jwts.parser().verifyWith(new SecretKeySpec(TEST_KEY, "HmacSHA256")).build();
        assertThat(parser.parseSignedClaims(access.value()).getPayload()).doesNotContainKey("sid");
        assertThat(parser.parseSignedClaims(refresh.value()).getPayload()).doesNotContainKey("sid");
    }

    @Test
    void reissuesRefreshTokensWithNewIdsAndTheExistingAbsoluteExpiry() {
        Instant loginExpiresAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(1));
        JwtProvider.IssuedToken first = provider.issueRefreshToken(USER_ID, loginExpiresAt);
        JwtProvider.IssuedToken replacement = provider.issueRefreshToken(USER_ID, loginExpiresAt);
        JwtProvider.VerifiedToken firstClaims = provider.verifyRefreshToken(first.value());
        JwtProvider.VerifiedToken replacementClaims = provider.verifyRefreshToken(replacement.value());

        assertThat(replacement.value()).isNotEqualTo(first.value());
        assertThat(replacementClaims.tokenId()).isNotEqualTo(firstClaims.tokenId());
        assertThat(replacementClaims.userId()).isEqualTo(firstClaims.userId());
        assertThat(replacementClaims.expiresAt()).isEqualTo(loginExpiresAt).isEqualTo(firstClaims.expiresAt());
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> provider.issueRefreshToken(USER_ID, Instant.now().minusSeconds(1)))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThatIllegalArgumentException().isThrownBy(() ->
                provider.issueRefreshToken(USER_ID, Instant.now().plus(Duration.ofDays(15))));
    }

    @Test
    void rejectsAccessAndRefreshTokensInTheOppositeContext() {
        String access = provider.issueAccessToken(USER_ID).value();
        String refresh = provider.issueRefreshToken(USER_ID).value();

        assertThatExceptionOfType(ApiException.class).isThrownBy(() -> provider.verifyRefreshToken(access))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertAccessRejected(refresh);
    }

    @Test
    void verifiesAnEquivalentTokenSignedWithNimbus() {
        JwtProvider.VerifiedToken token = provider.verifyAccessToken(signedAccess(claims -> {}));

        assertThat(token.userId()).isEqualTo(USER_ID);
        assertThat(token.tokenId()).isEqualTo(TOKEN_ID);
    }

    @Test
    void rejectsWrongSignaturesAlgorithmsIssuerAndAudience() {
        assertAccessRejected(signedAccess("fedcba9876543210".repeat(4).getBytes(StandardCharsets.UTF_8),
                MacAlgorithm.HS256, claims -> {}));
        assertAccessRejected(signedAccess(TEST_KEY, MacAlgorithm.HS384, claims -> {}));
        assertAccessRejected(signedAccess(claims -> claims.issuer("other-service")));
        assertAccessRejected(signedAccess(claims -> claims.audience(List.of("other-api"))));
    }

    @Test
    void rejectsExpiredAndFutureTokens() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertAccessRejected(signedAccess(claims -> claims.issuedAt(now.minusSeconds(60)).expiresAt(now)));
        assertAccessRejected(signedAccess(claims -> claims.issuedAt(now.plusSeconds(60))));
        assertAccessRejected(signedAccess(claims -> claims.notBefore(now.plusSeconds(60))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "jti", "iss", "aud", "iat", "exp", "token_type"})
    void rejectsMissingRequiredClaims(String claimName) {
        assertAccessRejected(signedAccess(claims -> claims.claims(values -> values.remove(claimName))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "jti"})
    void rejectsInvalidOrAbbreviatedUuidClaims(String claimName) {
        assertAccessRejected(signedAccess(claims -> claims.claim(claimName, "not-a-uuid")));
        assertAccessRejected(signedAccess(claims -> claims.claim(claimName, "1-1-1-1-1")));
    }

    @Test
    void rejectsBlankAndMalformedTokens() {
        assertAccessRejected(null);
        assertAccessRejected(" ");
        assertAccessRejected("invalid.jwt");
    }

    @Test
    void rejectsInvalidSigningKeyConfigurationAndKeepsSecretsOutOfToString() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JwtProvider(properties("not base64")))
                .withMessage("JWT_SECRET_KEY must be Base64-encoded");
        assertThatIllegalArgumentException().isThrownBy(() -> new JwtProvider(properties("YWJj")))
                .withMessage("JWT_SECRET_KEY must decode to at least 32 bytes");

        assertThat(properties(ENCODED_TEST_KEY).toString()).doesNotContain(ENCODED_TEST_KEY);
        JwtProvider.IssuedToken token = provider.issueAccessToken(USER_ID);
        assertThat(token.toString()).doesNotContain(token.value());
    }

    private static JwtProperties properties(String signingKey) {
        return new JwtProperties(signingKey, ISSUER, AUDIENCE, ACCESS_TTL, REFRESH_TTL);
    }

    private static String signedAccess(Consumer<JwtClaimsSet.Builder> customize) {
        return signedAccess(TEST_KEY, MacAlgorithm.HS256, customize);
    }

    private static String signedAccess(byte[] key, MacAlgorithm algorithm, Consumer<JwtClaimsSet.Builder> customize) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(USER_ID.toString())
                .id(TOKEN_ID.toString())
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plus(ACCESS_TTL))
                .claim("token_type", "access");
        customize.accept(claims);
        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .algorithm(algorithm).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).type("JWT").build(),
                claims.build())).getTokenValue();
    }

    private void assertAccessRejected(String token) {
        assertThatExceptionOfType(ApiException.class).isThrownBy(() -> provider.verifyAccessToken(token))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
