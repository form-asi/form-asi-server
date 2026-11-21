package com.formasiserver.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProperties;
import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.jwt.JwtProvider.IssuedToken;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.auth.security.RefreshTokenCookieFactory;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "AUTH_ALLOWED_ORIGINS=https://form.example,http://localhost:5173",
        "auth.refresh-cookie-secure=true"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String ME = "/api/v1/users/me";
    private static final String ORIGIN = "https://form.example";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private AuthTokenRedisRepository tokens;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<String> accessBlockKeys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID userId : userIds) {
            redis.delete("auth:refresh:{" + userId + "}");
            users.deleteById(userId);
        }
        if (!accessBlockKeys.isEmpty()) {
            redis.delete(accessBlockKeys);
        }
    }

    @Test
    void rotatesRefreshWithSameExpiryAndReturnsOnlyAccessInTheBody() throws Exception {
        UUID userId = member(20, UserStatus.PENDING);
        IssuedToken refresh = login(userId);
        MockHttpServletResponse response = mvc.perform(post(REFRESH).header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccess(userId))
                        .cookie(cookie(refresh)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse();

        String access = objectMapper.readTree(response.getContentAsString()).path("data").path("accessToken").asString();
        assertThat(jwtProvider.verifyAccessToken(access).userId()).isEqualTo(userId);
        String replacement = response.getCookie(RefreshTokenCookieFactory.NAME).getValue();
        assertThat(replacement).isNotEqualTo(refresh.value());
        assertThat(jwtProvider.verifyRefreshToken(replacement).expiresAt()).isEqualTo(refresh.expiresAt());
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/api/v1/auth", "Secure", "HttpOnly", "SameSite=None")
                .doesNotContain("Domain=");

        mvc.perform(post(REFRESH).header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookie(refresh)))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(post(LOGOUT).header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookie(refresh)))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(tokens.isCurrentRefresh(jwtProvider.verifyRefreshToken(replacement), replacement)).isTrue();

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .header("X-User-Id", UUID.randomUUID()).param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.nickname").isEmpty())
                .andExpect(jsonPath("$.data.age").value(20))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @ParameterizedTest
    @ValueSource(strings = {REFRESH, LOGOUT})
    void requiresOneExplicitAllowedOriginForCookieRequests(String path) throws Exception {
        UUID userId = member(20, UserStatus.ACTIVE);
        IssuedToken refresh = login(userId);

        mvc.perform(post(path).cookie(cookie(refresh)))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(post(path).header(HttpHeaders.ORIGIN, "http://localhost").cookie(cookie(refresh)))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(post(path).header(HttpHeaders.ORIGIN, ORIGIN, ORIGIN).cookie(cookie(refresh)))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(tokens.isCurrentRefresh(jwtProvider.verifyRefreshToken(refresh.value()), refresh.value())).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"13,PENDING,UNDER_MINIMUM_AGE,false", "20,SUSPENDED,ACCOUNT_SUSPENDED,true",
            "20,WITHDRAWN,ACCOUNT_WITHDRAWN,true"})
    void refreshChecksCurrentAgeAndAccountState(int age, UserStatus state, String error, boolean remainsCurrent)
            throws Exception {
        UUID userId = member(age, state);
        IssuedToken refresh = login(userId);

        mvc.perform(post(REFRESH).header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookie(refresh)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(error))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(tokens.isCurrentRefresh(jwtProvider.verifyRefreshToken(refresh.value()), refresh.value()))
                .isEqualTo(remainsCurrent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"same", "other", "expired", "missing"})
    void logoutRevokesRefreshAndOnlyBlocksValidAccessForTheSameMember(String accessKind) throws Exception {
        UUID userId = member(20, UserStatus.ACTIVE);
        IssuedToken refresh = login(userId);
        UUID accessUserId = accessKind.equals("other") ? UUID.randomUUID() : userId;
        IssuedToken validAccess = jwtProvider.issueAccessToken(accessUserId);
        JwtProvider.VerifiedToken verifiedAccess = jwtProvider.verifyAccessToken(validAccess.value());
        accessBlockKeys.add("auth:access:block:{" + accessUserId + "}:" + verifiedAccess.tokenId());
        String authorization = switch (accessKind) {
            case "same", "other" -> "Bearer " + validAccess.value();
            case "expired" -> "Bearer " + expiredAccess(userId);
            default -> "";
        };
        MockHttpServletRequestBuilder request = post(LOGOUT).header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookie(refresh));
        if (!authorization.isEmpty()) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                        .contains("refresh_token=;", "Path=/api/v1/auth", "Max-Age=0",
                                "Secure", "HttpOnly", "SameSite=None"));
        assertThat(tokens.isCurrentRefresh(jwtProvider.verifyRefreshToken(refresh.value()), refresh.value())).isFalse();
        assertThat(tokens.isAccessBlocked(verifiedAccess)).isEqualTo(accessKind.equals("same"));
    }

    private UUID member(int age, UserStatus state) {
        LocalDate birthDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(age);
        User user = User.of(UUID.randomUUID().toString());
        ReflectionTestUtils.setField(user, "birthDate", birthDate);
        ReflectionTestUtils.setField(user, "status", state);
        UUID userId = users.save(user).getUserId();
        userIds.add(userId);
        return userId;
    }

    private IssuedToken login(UUID userId) {
        IssuedToken refresh = jwtProvider.issueRefreshToken(userId);
        tokens.saveRefresh(userId, refresh);
        return refresh;
    }

    private String expiredAccess(UUID userId) {
        Instant now = Instant.now();
        byte[] key = Base64.getDecoder().decode(jwtProperties.secretKey());
        return Jwts.builder().subject(userId.toString()).id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer()).audience().add(jwtProperties.audience()).and()
                .issuedAt(Date.from(now.minusSeconds(60))).expiration(Date.from(now.minusSeconds(5)))
                .claim("token_type", "access")
                .signWith(new SecretKeySpec(key, "HmacSHA256"), Jwts.SIG.HS256).compact();
    }

    private static Cookie cookie(IssuedToken refresh) {
        return new Cookie(RefreshTokenCookieFactory.NAME, refresh.value());
    }
}
