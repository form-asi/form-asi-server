package com.formasiserver.domain.auth.oauth;

import com.formasiserver.domain.auth.jwt.JwtProvider.IssuedToken;
import com.formasiserver.domain.auth.security.AuthProperties;
import com.formasiserver.domain.auth.security.RefreshTokenCookieFactory;
import com.formasiserver.domain.auth.service.AuthService;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final AuthProperties authProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        IssuedToken refresh;
        try {
            refresh = authService.loginWithKakao((KakaoOAuth2User) authentication.getPrincipal());
        } catch (ApiException exception) {
            ErrorCode code = switch (exception.getErrorCode()) {
                case BIRTH_DATE_AGREEMENT_REQUIRED, BIRTH_DATE_UNAVAILABLE, BIRTH_DATE_VERIFICATION_REQUIRED,
                        UNDER_MINIMUM_AGE, ACCOUNT_SUSPENDED, ACCOUNT_WITHDRAWN -> exception.getErrorCode();
                default -> ErrorCode.INTERNAL_SERVER_ERROR;
            };
            redirectFailure(response, code);
            return;
        } catch (RuntimeException exception) {
            log.error("Kakao login completion failed ({})", exception.getClass().getSimpleName());
            redirectFailure(response, ErrorCode.INTERNAL_SERVER_ERROR);
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(refresh).toString());
        redirect(response, authProperties.loginRedirectUrl().toASCIIString());
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) {
        ErrorCode code = ErrorCode.KAKAO_AUTH_UNAVAILABLE;
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            code = switch (oauthException.getError().getErrorCode()) {
                case "access_denied" -> ErrorCode.KAKAO_LOGIN_CANCELLED;
                case "authorization_request_not_found", "invalid_state_parameter", "invalid_request" ->
                        ErrorCode.OAUTH_STATE_INVALID;
                default -> ErrorCode.KAKAO_AUTH_UNAVAILABLE;
            };
        }
        redirectFailure(response, code);
    }

    private void redirectFailure(HttpServletResponse response, ErrorCode code) {
        String location = UriComponentsBuilder.fromUriString(authProperties.loginRedirectUrl().toASCIIString())
                .queryParam("error", code.getCode())
                .build(true)
                .toUriString();
        redirect(response, location);
    }

    private static void redirect(HttpServletResponse response, String location) {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.LOCATION, location);
    }
}
