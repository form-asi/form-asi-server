package com.formasiserver.global.security;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.global.exception.ErrorCode;
import com.formasiserver.global.response.BaseResponse;
import com.formasiserver.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ErrorCode error = ErrorCode.FORBIDDEN;
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.status() == UserStatus.PENDING) {
            error = ErrorCode.PROFILE_REQUIRED;
        }
        write(response, error);
    }

    public void write(HttpServletResponse response, ErrorCode error) throws IOException {
        response.setStatus(error.getStatus().value());
        if (error == ErrorCode.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                BaseResponse.fail(ErrorResponse.of(error.getCode(), error.getMessage())));
    }
}
