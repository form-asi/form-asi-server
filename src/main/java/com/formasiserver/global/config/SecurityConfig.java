package com.formasiserver.global.config;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.auth.security.AuthProperties;
import com.formasiserver.domain.auth.security.JwtAuthenticationFilter;
import com.formasiserver.domain.auth.security.SecurityErrorHandler;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.global.exception.ErrorCode;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    private static final String API_PATH = "/api/v1/**";
    private static final int KAKAO_SESSION_TIMEOUT_SECONDS = 300;

    @Bean
    public SecurityErrorHandler securityErrorHandler(ObjectMapper objectMapper) {
        return new SecurityErrorHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, JwtProvider jwtProvider, AuthTokenRedisRepository tokenRepository,
            UserRepository userRepository, SecurityErrorHandler errorHandler, AuthProperties properties) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtProvider, tokenRepository, userRepository,
                errorHandler, new NegatedRequestMatcher(PathPatternRequestMatcher.pathPattern(API_PATH)));

        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .addFilterAt(corsFilter(properties, errorHandler), CorsFilter.class)
                .addFilterBefore(jwtFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api-docs", "/api-docs/**", "/swagger-ui/**", "/v3/api-docs",
                                "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/users/profile-options",
                                "/api/v1/users/nickname-availability").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/me/onboarding").authenticated()
                        .anyRequest().denyAll())
                .build();
    }

    private CorsFilter corsFilter(AuthProperties properties, SecurityErrorHandler errorHandler) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(API_PATH, config);
        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                ServletServerHttpResponse servletResponse = (ServletServerHttpResponse) response;
                errorHandler.write(servletResponse.getServletResponse(), ErrorCode.FORBIDDEN);
            }
        });
        return filter;
    }

    @Bean
    public JdkClientHttpRequestFactory kakaoRequestFactory(ClientRegistrationRepository registrations) {
        ClientRegistration registration = registrations.findByRegistrationId("kakao");
        Assert.notNull(registration, "Kakao OAuth registration is required");
        requireSetting(registration.getClientId(), "KAKAO_CLIENT_ID");
        requireSetting(registration.getClientSecret(), "KAKAO_CLIENT_SECRET");
        requireSetting(registration.getRedirectUri(), "KAKAO_REDIRECT_URI");

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return requestFactory;
    }

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> kakaoTokenClient(
            JdkClientHttpRequestFactory kakaoRequestFactory) {
        RestClient restClient = RestClient.builder()
                .requestFactory(kakaoRequestFactory)
                .configureMessageConverters(converters -> converters.disableDefaults()
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(restClient);
        return request -> {
            try {
                return client.getTokenResponse(request);
            } catch (OAuth2AuthorizationException exception) {
                throw new OAuth2AuthorizationException(new OAuth2Error(ErrorCode.KAKAO_AUTH_UNAVAILABLE.getCode()));
            }
        };
    }

    public OAuth2AuthorizationRequestResolver kakaoAuthorizationRequestResolver(
            ClientRegistrationRepository registrations) {
        var delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return applyKakaoSessionTimeout(request, delegate.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
                return applyKakaoSessionTimeout(request, delegate.resolve(request, registrationId));
            }
        };
    }

    private static OAuth2AuthorizationRequest applyKakaoSessionTimeout(
            HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest != null) {
            request.getSession().setMaxInactiveInterval(KAKAO_SESSION_TIMEOUT_SECONDS);
        }
        return authorizationRequest;
    }

    private static void requireSetting(String value, String name) {
        Assert.state(StringUtils.hasText(value) && !value.startsWith("${"), () -> name + " must be configured");
    }

}
