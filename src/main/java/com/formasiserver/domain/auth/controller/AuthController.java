package com.formasiserver.domain.auth.controller;

import com.formasiserver.domain.auth.dto.response.AccessTokenResponse;
import com.formasiserver.domain.auth.security.BearerTokenResolver;
import com.formasiserver.domain.auth.security.RefreshTokenCookieFactory;
import com.formasiserver.domain.auth.service.AuthService;
import com.formasiserver.domain.auth.service.AuthService.RefreshedTokens;
import com.formasiserver.global.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AccessTokenResponse>> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.NAME, required = false) @Nullable String refreshToken) {
        RefreshedTokens tokens = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(tokens.refresh()).toString())
                .body(BaseResponse.success(new AccessTokenResponse(tokens.access().value())));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @RequestHeader HttpHeaders headers,
            @CookieValue(name = RefreshTokenCookieFactory.NAME, required = false) @Nullable String refreshToken) {
        authService.logout(refreshToken, BearerTokenResolver.resolve(headers.getFirst(HttpHeaders.AUTHORIZATION)));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .body(BaseResponse.success(null));
    }

}
