package com.formasiserver.domain.auth.service;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.jwt.JwtProvider.IssuedToken;
import com.formasiserver.domain.auth.jwt.JwtProvider.VerifiedToken;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.domain.user.service.BirthDateValidator;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider jwtProvider;
    private final AuthTokenRedisRepository tokenRepository;
    private final UserRepository userRepository;
    private final BirthDateValidator birthDateValidator;

    public RefreshedTokens refresh(@Nullable String refreshToken) {
        VerifiedToken refresh = jwtProvider.verifyRefreshToken(refreshToken);
        if (!tokenRepository.isCurrentRefresh(refresh, refreshToken)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        UUID userId = refresh.userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        switch (user.getStatus()) {
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case PENDING, ACTIVE -> { }
        }
        try {
            birthDateValidator.validate(user.getBirthDate());
        } catch (ApiException exception) {
            if (exception.getErrorCode() == ErrorCode.UNDER_MINIMUM_AGE
                    && !tokenRepository.revokeRefresh(userId, refreshToken)) {
                throw new ApiException(ErrorCode.UNAUTHORIZED);
            }
            throw exception;
        }

        IssuedToken replacement = jwtProvider.issueRefreshToken(userId, refresh.expiresAt());
        IssuedToken access = jwtProvider.issueAccessToken(userId);
        if (!tokenRepository.rotateRefresh(userId, refreshToken, replacement)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return new RefreshedTokens(access, replacement);
    }

    public void logout(@Nullable String refreshToken, @Nullable String accessToken) {
        VerifiedToken refresh = jwtProvider.verifyRefreshToken(refreshToken);
        VerifiedToken access = verifyOptionalAccess(accessToken);
        boolean revoked = access != null && refresh.userId().equals(access.userId())
                ? tokenRepository.revokeRefreshAndBlockAccess(refresh.userId(), refreshToken, access)
                : tokenRepository.revokeRefresh(refresh.userId(), refreshToken);
        if (!revoked) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }

    private @Nullable VerifiedToken verifyOptionalAccess(@Nullable String token) {
        if (token == null) {
            return null;
        }
        try {
            return jwtProvider.verifyAccessToken(token);
        } catch (ApiException exception) {
            return null;
        }
    }

    public record RefreshedTokens(IssuedToken access, IssuedToken refresh) {
    }
}
