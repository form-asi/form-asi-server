package com.formasiserver.global.exception;

import com.formasiserver.global.response.BaseResponse;
import com.formasiserver.global.response.ErrorResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.databind.exc.MismatchedInputException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<BaseResponse<Void>> handleApiException(ApiException e) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("API exception: {}", code.getCode(), e);
        }
        return ResponseEntity.status(code.getStatus())
                .body(BaseResponse.fail(ErrorResponse.of(code.getCode(), code.getMessage(), e.getValidationErrors())));
    }

    // MVC 실행 중 발생한 보안 예외. 필터 단계의 예외는 Security 설정에서 별도로 처리한다.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<BaseResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        return toResponseEntity(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        return toResponseEntity(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleUnexpectedException(Exception e) {
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
        } else if (ex instanceof HttpMessageNotReadableException
                && ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().getFirst().getPropertyName();
            if (field != null) {
                code = ErrorCode.VALIDATION_FAILED;
                errors = List.of(new ErrorResponse.ValidationError(field, "허용된 형식과 값을 확인해주세요."));
            }
        }

        if (statusCode.is5xxServerError()) {
            log.error("Spring MVC server error", ex);
        }

        BaseResponse<Void> response = BaseResponse.fail(
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

    private ResponseEntity<BaseResponse<Void>> toResponseEntity(ErrorCode code) {
        return ResponseEntity.status(code.getStatus())
                .body(BaseResponse.fail(ErrorResponse.of(code.getCode(), code.getMessage())));
    }

}
