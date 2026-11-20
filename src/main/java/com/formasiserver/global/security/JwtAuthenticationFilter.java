package com.formasiserver.global.security;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.jwt.JwtProvider.VerifiedToken;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.auth.security.BearerTokenResolver;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AuthTokenRedisRepository tokenRepository;
    private final UserRepository userRepository;
    private final SecurityErrorHandler errorHandler;
    private final RequestMatcher excludedRequests;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return excludedRequests.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = BearerTokenResolver.resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            try {
                authenticate(token);
            } catch (ApiException exception) {
                errorHandler.write(response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        VerifiedToken verified = jwtProvider.verifyAccessToken(token);
        if (tokenRepository.isAccessBlocked(verified)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(verified.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        AuthenticatedUser principal = switch (user.getStatus()) {
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case PENDING, ACTIVE -> new AuthenticatedUser(user.getUserId(), user.getRole(), user.getStatus());
        };
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
        SecurityContextHolder.setContext(context);
    }
}
