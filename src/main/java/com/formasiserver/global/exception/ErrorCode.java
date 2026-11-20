package com.formasiserver.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "올바르지 않은 요청입니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해주세요."),
    KAKAO_LOGIN_CANCELLED(HttpStatus.BAD_REQUEST, "KAKAO_LOGIN_CANCELLED", "카카오 로그인이 취소됐습니다."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "카카오 로그인을 처음부터 다시 시작해주세요."),
    BIRTH_DATE_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "BIRTH_DATE_AGREEMENT_REQUIRED", "카카오 생년월일 제공에 동의해주세요."),
    BIRTH_DATE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "BIRTH_DATE_UNAVAILABLE", "생년월일을 확인할 수 없습니다."),
    BIRTH_DATE_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "BIRTH_DATE_VERIFICATION_REQUIRED", "음력 생일의 윤달 여부를 확인해주세요."),
    UNDER_MINIMUM_AGE(HttpStatus.FORBIDDEN, "UNDER_MINIMUM_AGE", "만 14세 이상만 이용할 수 있습니다."),
    KAKAO_AUTH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "KAKAO_AUTH_UNAVAILABLE", "카카오 로그인에 연결할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "정지된 계정입니다."),
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "ACCOUNT_WITHDRAWN", "탈퇴한 계정입니다."),
    PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "PROFILE_REQUIRED", "프로필을 먼저 입력해주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "요청한 응답 형식을 제공할 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

}
