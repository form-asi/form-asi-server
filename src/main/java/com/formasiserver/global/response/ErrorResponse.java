package com.formasiserver.global.response;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<ValidationError> errors
) {

    public ErrorResponse {
        errors = List.copyOf(errors);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }

    public static ErrorResponse of(String code, String message, List<ValidationError> errors) {
        return new ErrorResponse(code, message, errors);
    }

    public record ValidationError(String field, String message) {
    }

}
