package com.formasiserver.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.auth.security.AuthProperties;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.global.config.SecurityConfig;
import com.formasiserver.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(KakaoOAuth2LoginTest.OAuthTestConfig.class)
@MockitoBean(types = {JwtProvider.class, AuthTokenRedisRepository.class, UserRepository.class})
class KakaoOAuth2LoginTest {

    private static final String AUTHORIZATION_PATH = "/oauth2/authorization/kakao";
    private static final String CALLBACK_PATH = "/login/oauth2/code/kakao";
    private static final AtomicInteger TOKEN_STATUS = new AtomicInteger();
    private static final AtomicInteger USER_STATUS = new AtomicInteger();
    private static final AtomicInteger TOKEN_CALLS = new AtomicInteger();
    private static final AtomicReference<String> TOKEN_BODY = new AtomicReference<>();
    private static final AtomicReference<String> USER_AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer KAKAO = startStub();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TOKEN_STATUS.set(200);
        USER_STATUS.set(200);
        TOKEN_CALLS.set(0);
        TOKEN_BODY.set(null);
        USER_AUTHORIZATION.set(null);
    }

    @AfterAll
    static void stopStub() {
        KAKAO.stop(0);
    }

    @Test
    void exchangesTheCodeAndRejectsCallbackReplayInTheSameSession() throws Exception {
        Authorization authorization = startAuthorization();
        assertThat(authorization.session().getMaxInactiveInterval()).isEqualTo(300);

        mvc.perform(callback(authorization.state()).session(authorization.session()))
                .andExpect(status().isOk())
                .andExpect(content().string("123456789012|2012-02-29"));
        assertThat(TOKEN_BODY.get()).contains("grant_type=authorization_code", "code=stub-code",
                "client_id=stub-client", "client_secret=stub-secret");
        assertThat(USER_AUTHORIZATION.get()).isEqualTo("Bearer stub-access-token");
        assertThat(authorization.session().isInvalid()).isFalse();

        mvc.perform(callback(authorization.state()).session(authorization.session()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("OAUTH_STATE_INVALID"));
        assertThat(TOKEN_CALLS.get()).isEqualTo(1);
    }

    @Test
    void mapsUserCancellationBeforeTheTokenExchange() throws Exception {
        Authorization authorization = startAuthorization();
        mvc.perform(get(CALLBACK_PATH).secure(true).session(authorization.session())
                        .param("state", authorization.state()).param("error", "access_denied")
                        .param("error_description", "provider-only detail"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("KAKAO_LOGIN_CANCELLED"));

        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void rejectsMismatchedStateOrMissingSessionBeforeTheTokenExchange() throws Exception {
        Authorization authorization = startAuthorization();

        mvc.perform(callback(authorization.state()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("OAUTH_STATE_INVALID"));

        mvc.perform(callback("wrong-state").session(authorization.session()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("OAUTH_STATE_INVALID"));
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void rejectsCallbacksWithAnExpiredSessionCookie() throws Exception {
        Authorization authorization = startAuthorization();
        String sessionId = authorization.session().getId();
        authorization.session().invalidate();

        mvc.perform(callback(authorization.state()).cookie(new Cookie("JSESSIONID", sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("OAUTH_STATE_INVALID"));
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void rejectsCallbacksWithoutStateBeforeTheTokenExchange() throws Exception {
        Authorization authorization = startAuthorization();
        mvc.perform(get(CALLBACK_PATH).secure(true).session(authorization.session())
                        .param("code", "stub-code"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("OAUTH_STATE_INVALID"));

        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void mapsProviderTokenAndUserInfoFailuresWithoutLeakingDetails() throws Exception {
        TOKEN_STATUS.set(400);
        Authorization tokenAuthorization = startAuthorization();
        mvc.perform(callback(tokenAuthorization.state()).session(tokenAuthorization.session()))
                .andExpect(status().isBadGateway())
                .andExpect(content().string("KAKAO_AUTH_UNAVAILABLE"));
        assertThat(TOKEN_CALLS.get()).isEqualTo(1);
        assertThat(USER_AUTHORIZATION.get()).isNull();

        TOKEN_STATUS.set(200);
        USER_STATUS.set(503);
        Authorization userAuthorization = startAuthorization();
        mvc.perform(callback(userAuthorization.state()).session(userAuthorization.session()))
                .andExpect(status().isBadGateway())
                .andExpect(content().string("KAKAO_AUTH_UNAVAILABLE"));
        assertThat(TOKEN_CALLS.get()).isEqualTo(2);
    }

    private Authorization startAuthorization() throws Exception {
        MvcResult result = mvc.perform(get(AUTHORIZATION_PATH).secure(true))
                .andExpect(status().isFound())
                .andReturn();
        String redirect = result.getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith(stubUrl("/authorize?"));
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
                        ? """
                          {"id":123456789012,"kakao_account":{"birthyear":"2012","birthday":"0229","birthday_type":"SOLAR"}}
                          """
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

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, KakaoOAuth2UserService.class})
    static class OAuthTestConfig {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(List.of());
        }

        @Bean
        ClientRegistrationRepository clientRegistrations() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("kakao")
                    .clientId("stub-client")
                    .clientSecret("stub-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("https://localhost" + CALLBACK_PATH)
                    .scope("birthday", "birthyear")
                    .authorizationUri(stubUrl("/authorize"))
                    .tokenUri(stubUrl("/token"))
                    .userInfoUri(stubUrl("/v2/user/me"))
                    .userNameAttributeName("id")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }

        @Bean
        @Order(0)
        SecurityFilterChain oauthTestChain(HttpSecurity http, SecurityConfig securityConfig,
                ClientRegistrationRepository registrations,
                KakaoOAuth2UserService kakaoUserService,
                OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> kakaoTokenClient) throws Exception {
            var resolver = securityConfig.kakaoAuthorizationRequestResolver(registrations);
            return http.securityMatcher(AUTHORIZATION_PATH, CALLBACK_PATH)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .requestCache(cache -> cache.disable())
                    .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                    .oauth2Login(login -> login
                            .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                            .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                            .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(kakaoTokenClient))
                            .userInfoEndpoint(endpoint -> endpoint.userService(kakaoUserService))
                            .successHandler((request, response, authentication) -> {
                                KakaoOAuth2User user = (KakaoOAuth2User) authentication.getPrincipal();
                                response.getWriter().print(user.getName() + "|" + user.requireBirthDate());
                            })
                            .failureHandler((request, response, exception) -> {
                                ErrorCode error = ErrorCode.KAKAO_AUTH_UNAVAILABLE;
                                if (exception instanceof OAuth2AuthenticationException oauthException) {
                                    error = switch (oauthException.getError().getErrorCode()) {
                                        case "access_denied" -> ErrorCode.KAKAO_LOGIN_CANCELLED;
                                        case "authorization_request_not_found", "invalid_state_parameter", "invalid_request" ->
                                                ErrorCode.OAUTH_STATE_INVALID;
                                        default -> ErrorCode.KAKAO_AUTH_UNAVAILABLE;
                                    };
                                }
                                response.setStatus(error.getStatus().value());
                                response.getWriter().print(error.getCode());
                            }))
                    .build();
        }
    }
}
