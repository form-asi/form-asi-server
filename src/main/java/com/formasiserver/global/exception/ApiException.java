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
        this(errorCode, errorCode.getMessage(), cause, validationErrors);
    }

    private ApiException(ErrorCode errorCode, String message, @Nullable Throwable cause,
            List<ValidationError> validationErrors) {
        super(message, cause);
        this.errorCode = errorCode;
        this.validationErrors = List.copyOf(validationErrors);
    }

    public static ApiException validationFailed(List<ValidationError> validationErrors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, null, validationErrors);
    }

    public static ApiException insufficientHelpTime(int requiredMinutes, long availableMinutes) {
        String message = availableMinutes < 0
                ? "도움 시간이 마이너스인 동안은 설문을 올릴 수 없어요."
                : "%d분이 모자라요. 응답 수나 소요 시간을 줄여주세요.".formatted(requiredMinutes - availableMinutes);
        return new ApiException(ErrorCode.INSUFFICIENT_HELP_TIME, message, null, List.of());
    }
}
