package com.formasiserver.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.auth.security.RefreshTokenCookieFactory;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.repository.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"AUTH_ALLOWED_ORIGINS=https://form.example", "auth.refresh-cookie-secure=true"})
@AutoConfigureMockMvc
class KakaoOAuth2LoginTest {

    private static final String AUTHORIZATION_PATH = "/api/v1/auth/login/kakao";
    private static final String CALLBACK_PATH = "/login/oauth2/code/kakao";
    private static final String FRONTEND_ORIGIN = "https://form.example";
    private static final String FRONTEND_CALLBACK = FRONTEND_ORIGIN + "/auth/callback";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String ME_PATH = "/api/v1/users/me";
    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 2, 29);
    private static final AtomicInteger TOKEN_STATUS = new AtomicInteger();
    private static final AtomicInteger USER_STATUS = new AtomicInteger();
    private static final AtomicInteger TOKEN_CALLS = new AtomicInteger();
    private static final AtomicReference<String> TOKEN_BODY = new AtomicReference<>();
    private static final AtomicReference<String> USER_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> USER_BODY = new AtomicReference<>();
    private static final HttpServer KAKAO = startStub();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @MockitoSpyBean
    private AuthTokenRedisRepository tokens;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper objectMapper;

    private final String kakaoId = Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));

    @DynamicPropertySource
    static void kakaoEndpoints(DynamicPropertyRegistry properties) {
        String provider = "spring.security.oauth2.client.provider.kakao.";
        properties.add(provider + "authorization-uri", () -> stubUrl("/authorize"));
        properties.add(provider + "token-uri", () -> stubUrl("/token"));
        properties.add(provider + "user-info-uri", () -> stubUrl("/v2/user/me"));
        properties.add("KAKAO_CLIENT_ID", () -> "stub-client");
        properties.add("KAKAO_CLIENT_SECRET", () -> "stub-secret");
        properties.add("KAKAO_REDIRECT_URI", () -> "https://localhost" + CALLBACK_PATH);
        properties.add("AUTH_LOGIN_REDIRECT_URL", () -> FRONTEND_CALLBACK);
    }

    @BeforeEach
    void setUp() {
        TOKEN_STATUS.set(200);
        USER_STATUS.set(200);
        TOKEN_CALLS.set(0);
        TOKEN_BODY.set(null);
        USER_AUTHORIZATION.set(null);
        USER_BODY.set("{\"id\":" + kakaoId + "}");
    }

    @AfterEach
    void cleanUp() {
        users.findByKakaoId(kakaoId).ifPresent(user -> {
            redis.delete("auth:refresh:{" + user.getUserId() + "}");
            users.delete(user);
        });
    }

    @AfterAll
    static void stopStub() {
        KAKAO.stop(0);
    }

    @Test
    void exchangesTheCodeAndRejectsCallbackReplayInTheSameSession() throws Exception {
        Authorization authorization = startAuthorization();
        assertThat(authorization.session().getMaxInactiveInterval()).isEqualTo(300);

        Cookie refresh = successfulLogin(authorization);
        User member = users.findByKakaoId(kakaoId).orElseThrow();
        assertThat(member.getBirthDate()).isNull();
        assertThat(member.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(jwtProvider.verifyRefreshToken(refresh.getValue()).userId()).isEqualTo(member.getUserId());
        assertThat(TOKEN_BODY.get()).contains("grant_type=authorization_code", "code=stub-code",
                "client_id=stub-client", "client_secret=stub-secret");
        assertThat(USER_AUTHORIZATION.get()).isEqualTo("Bearer stub-access-token");
        assertThat(authorization.session().isInvalid()).isFalse();
        assertThat(authorization.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNull();

        String access = exchangeRefresh(refresh);
        mvc.perform(get(ME_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(member.getUserId().toString()))
                .andExpect(jsonPath("$.data.age").isEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        expectFailure(mvc.perform(callback(authorization.state()).session(authorization.session())),
                "OAUTH_STATE_INVALID");
        assertThat(TOKEN_CALLS.get()).isEqualTo(1);
    }

    @Test
    void mapsUserCancellationBeforeTheTokenExchange() throws Exception {
        Authorization authorization = startAuthorization();
        expectFailure(mvc.perform(get(CALLBACK_PATH).secure(true).session(authorization.session())
                        .param("state", authorization.state()).param("error", "access_denied")
                        .param("error_description", "provider-only detail")),
                "KAKAO_LOGIN_CANCELLED");

        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void rejectsMismatchedStateOrMissingSessionBeforeTheTokenExchange() throws Exception {
        Authorization authorization = startAuthorization();

        expectFailure(mvc.perform(callback(authorization.state())), "OAUTH_STATE_INVALID");

        expectFailure(mvc.perform(callback("wrong-state").session(authorization.session())), "OAUTH_STATE_INVALID");
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void rejectsCallbacksWithAnExpiredSessionCookie() throws Exception {
        Authorization authorization = startAuthorization();
        String sessionId = authorization.session().getId();
        authorization.session().invalidate();

        expectFailure(mvc.perform(callback(authorization.state()).cookie(new Cookie("JSESSIONID", sessionId))),
                "OAUTH_STATE_INVALID");
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void rejectsCallbacksWithoutStateBeforeTheTokenExchange() throws Exception {
        Authorization authorization = startAuthorization();
        expectFailure(mvc.perform(get(CALLBACK_PATH).secure(true).session(authorization.session())
                        .param("code", "stub-code")),
                "OAUTH_STATE_INVALID");

        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void mapsProviderTokenAndUserInfoFailuresWithoutLeakingDetails() throws Exception {
        TOKEN_STATUS.set(400);
        Authorization tokenAuthorization = startAuthorization();
        expectFailure(mvc.perform(callback(tokenAuthorization.state()).session(tokenAuthorization.session())),
                "KAKAO_AUTH_UNAVAILABLE");
        assertThat(TOKEN_CALLS.get()).isEqualTo(1);
        assertThat(USER_AUTHORIZATION.get()).isNull();

        TOKEN_STATUS.set(200);
        USER_STATUS.set(503);
        Authorization userAuthorization = startAuthorization();
        expectFailure(mvc.perform(callback(userAuthorization.state()).session(userAuthorization.session())),
                "KAKAO_AUTH_UNAVAILABLE");
        assertThat(TOKEN_CALLS.get()).isEqualTo(2);
    }

    @Test
    void returningMemberKeepsStoredBirthDateAndExistingAccess() throws Exception {
        Cookie firstRefresh = successfulLogin(startAuthorization());
        String access = exchangeRefresh(firstRefresh);
        User member = users.findByKakaoId(kakaoId).orElseThrow();
        ReflectionTestUtils.setField(member, "birthDate", BIRTH_DATE);
        ReflectionTestUtils.setField(member, "status", UserStatus.ACTIVE);
        users.saveAndFlush(member);

        Cookie secondRefresh = successfulLogin(startAuthorization());

        assertThat(jwtProvider.verifyRefreshToken(secondRefresh.getValue()).userId()).isEqualTo(member.getUserId());
        assertThat(users.findByKakaoId(kakaoId)).get().extracting(User::getBirthDate).isEqualTo(BIRTH_DATE);
        mvc.perform(post(REFRESH_PATH).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN).cookie(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get(ME_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(exchangeRefresh(secondRefresh)).isNotBlank();
    }

    @Test
    void pendingMemberCanReturnToTheSameAccount() throws Exception {
        Cookie firstRefresh = successfulLogin(startAuthorization());
        Cookie secondRefresh = successfulLogin(startAuthorization());
        User member = users.findByKakaoId(kakaoId).orElseThrow();

        assertThat(member.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(jwtProvider.verifyRefreshToken(firstRefresh.getValue()).userId()).isEqualTo(member.getUserId());
        assertThat(jwtProvider.verifyRefreshToken(secondRefresh.getValue()).userId()).isEqualTo(member.getUserId());
        mvc.perform(post(REFRESH_PATH).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN).cookie(firstRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentFirstLoginsUseOneMemberAndKeepOnlyOneCurrentRefresh() throws Exception {
        Authorization first = startAuthorization();
        Authorization second = startAuthorization();
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Cookie> firstLogin = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return successfulLogin(first);
            });
            Future<Cookie> secondLogin = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return successfulLogin(second);
            });

            String firstToken = firstLogin.get(10, TimeUnit.SECONDS).getValue();
            String secondToken = secondLogin.get(10, TimeUnit.SECONDS).getValue();
            User member = users.findByKakaoId(kakaoId).orElseThrow();
            JwtProvider.VerifiedToken firstClaims = jwtProvider.verifyRefreshToken(firstToken);
            JwtProvider.VerifiedToken secondClaims = jwtProvider.verifyRefreshToken(secondToken);
            assertThat(firstClaims.userId()).isEqualTo(member.getUserId());
            assertThat(secondClaims.userId()).isEqualTo(member.getUserId());
            assertThat(new boolean[] {tokens.isCurrentRefresh(firstClaims, firstToken),
                    tokens.isCurrentRefresh(secondClaims, secondToken)}).containsExactlyInAnyOrder(true, false);
        }
    }

    @ParameterizedTest
    @CsvSource({"SUSPENDED,20,ACCOUNT_SUSPENDED", "WITHDRAWN,20,ACCOUNT_WITHDRAWN",
            "PENDING,13,UNDER_MINIMUM_AGE"})
    void existingMemberMustPassStoredAgeAndStatusChecks(UserStatus state, int age, String error) throws Exception {
        User member = User.of(kakaoId);
        ReflectionTestUtils.setField(member, "birthDate", LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(age));
        ReflectionTestUtils.setField(member, "status", state);
        users.saveAndFlush(member);
        Authorization authorization = startAuthorization();

        expectFailure(mvc.perform(callback(authorization.state()).session(authorization.session())), error);
        assertThat(redis.hasKey("auth:refresh:{" + member.getUserId() + "}")).isFalse();
    }

    @Test
    void redisWriteFailureDoesNotSendACookieAndCanRecoverOnTheNextLogin() throws Exception {
        doThrow(new RedisConnectionFailureException("test")).doCallRealMethod().when(tokens).saveRefresh(any(), any());
        Authorization authorization = startAuthorization();
        expectFailure(mvc.perform(callback(authorization.state())
                .session(authorization.session())), "INTERNAL_SERVER_ERROR");
        User member = users.findByKakaoId(kakaoId).orElseThrow();
        assertThat(redis.hasKey("auth:refresh:{" + member.getUserId() + "}")).isFalse();

        Cookie refresh = successfulLogin(startAuthorization());
        assertThat(jwtProvider.verifyRefreshToken(refresh.getValue()).userId()).isEqualTo(member.getUserId());
    }

    private String exchangeRefresh(Cookie refresh) throws Exception {
        MockHttpServletResponse response = mvc.perform(post(REFRESH_PATH)
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN).cookie(refresh))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString()).path("data").path("accessToken").asString();
    }

    private Cookie successfulLogin(Authorization authorization) throws Exception {
        MockHttpServletResponse response = mvc.perform(callback(authorization.state()).session(authorization.session()))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
                .andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND_CALLBACK);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/api/v1/auth", "Secure", "HttpOnly", "SameSite=None");
        Cookie refresh = response.getCookie(RefreshTokenCookieFactory.NAME);
        assertThat(refresh).isNotNull();
        assertThat(response.getHeader(HttpHeaders.LOCATION)).doesNotContain(refresh.getValue());
        return refresh;
    }

    private static void expectFailure(ResultActions result, String error) throws Exception {
        String location = result.andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isEqualTo(FRONTEND_CALLBACK + "?error=" + error);
    }

    private Authorization startAuthorization() throws Exception {
        MvcResult result = mvc.perform(get(AUTHORIZATION_PATH).secure(true))
                .andExpect(status().isFound())
                .andReturn();
        String redirect = result.getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith(stubUrl("/authorize?")).doesNotContain("birthday", "birthyear");
        String state = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams().getFirst("state");
        assertThat(state).isNotBlank();
        return new Authorization(UriUtils.decode(state, StandardCharsets.UTF_8),
                (MockHttpSession) result.getRequest().getSession(false));
    }

    private static MockHttpServletRequestBuilder callback(String state) {
        return get(CALLBACK_PATH).secure(true).param("code", "stub-code").param("state", state);
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> {
                TOKEN_CALLS.incrementAndGet();
                TOKEN_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                int status = TOKEN_STATUS.get();
                reply(exchange, status, status == 200
                        ? """
                          {"access_token":"stub-access-token","token_type":"bearer","expires_in":300}
                          """
                        : """
                          {"error":"access_denied","error_description":"provider-only token failure"}
                          """);
            });
            server.createContext("/v2/user/me", exchange -> {
                USER_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                int status = USER_STATUS.get();
                reply(exchange, status, status == 200
                        ? USER_BODY.get()
                        : """
                          {"message":"provider-only user failure"}
                          """);
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        try (exchange) {
            byte[] content = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(status, content.length);
            exchange.getResponseBody().write(content);
        }
    }

    private static String stubUrl(String path) {
        return "http://127.0.0.1:" + KAKAO.getAddress().getPort() + path;
    }

    private record Authorization(String state, MockHttpSession session) {
    }

}
