package com.formasiserver.global.exception;

import com.formasiserver.global.response.ApiResponse;
import com.formasiserver.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("API exception: {}", code.getCode(), e);
        }
        return toResponseEntity(code);
    }

    // MVC 실행 중 발생한 보안 예외. 필터 단계의 예외는 Security 설정에서 별도로 처리한다.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        return toResponseEntity(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        return toResponseEntity(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
        log.error("Unexpected server error", e);
        return toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        ErrorCode code = resolveErrorCode(ex, statusCode);
        List<ErrorResponse.ValidationError> errors = List.of();

        if (ex instanceof MethodArgumentNotValidException validationException) {
            errors = validationException.getBindingResult().getAllErrors().stream()
                    .map(error -> new ErrorResponse.ValidationError(
                            error instanceof FieldError fieldError
                                    ? fieldError.getField() : error.getObjectName(),
                            error.getDefaultMessage()
                    ))
                    .toList();
        }

        if (statusCode.is5xxServerError()) {
            log.error("Spring MVC server error", ex);
        }

        ApiResponse<Void> response = ApiResponse.fail(
                ErrorResponse.of(code.getCode(), code.getMessage(), errors)
        );

        // 405의 Allow 등 프레임워크가 지정한 상태와 헤더를 유지한다.
        return super.handleExceptionInternal(ex, response, headers, statusCode, request);
    }

    private ErrorCode resolveErrorCode(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MethodArgumentNotValidException) {
            return ErrorCode.VALIDATION_FAILED;
        }

        return switch (statusCode.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> statusCode.is5xxServerError()
                    ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.INVALID_REQUEST;
        };
    }

    private ResponseEntity<ApiResponse<Void>> toResponseEntity(ErrorCode code) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(code.getCode(), code.getMessage())));
    }

}
