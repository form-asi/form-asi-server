package com.formasiserver.global.exception;

import com.formasiserver.global.response.ErrorResponse.ValidationError;
import java.util.List;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ValidationError> validationErrors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, List.of());
    }

    public ApiException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, cause, List.of());
    }

    private ApiException(ErrorCode errorCode, @Nullable Throwable cause, List<ValidationError> validationErrors) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.validationErrors = List.copyOf(validationErrors);
    }

    public static ApiException validationFailed(List<ValidationError> validationErrors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, null, validationErrors);
    }
}
