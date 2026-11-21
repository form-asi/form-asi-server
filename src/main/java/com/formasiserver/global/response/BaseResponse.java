package com.formasiserver.global.response;

public record BaseResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(true, data, null);
    }

    public static BaseResponse<Void> fail(ErrorResponse error) {
        return new BaseResponse<>(false, null, error);
    }

}
