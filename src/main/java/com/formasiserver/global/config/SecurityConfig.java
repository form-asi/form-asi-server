package com.formasiserver.global.config;

import com.formasiserver.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class SecurityConfig {

    private static final int KAKAO_SESSION_TIMEOUT_SECONDS = 300;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api-docs", "/api-docs/**", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .build();
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
