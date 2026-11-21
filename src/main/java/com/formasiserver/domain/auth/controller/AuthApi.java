package com.formasiserver.domain.auth.controller;

import static com.formasiserver.global.config.SwaggerConfig.REFRESH_COOKIE_AUTH;

import com.formasiserver.domain.auth.dto.response.AccessTokenResponse;
import com.formasiserver.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "로그인 후 토큰을 발급받거나 로그아웃할 때 사용합니다.")
public interface AuthApi {

    @Operation(summary = "토큰 재발급", description = """
            카카오 로그인 직후나 Access 토큰이 만료됐을 때 호출하세요.
            허용된 Origin에서 브라우저의 `refresh_token` 쿠키를 보내면 새 Access 토큰을 `data.accessToken`으로 받습니다.
            Refresh 쿠키도 새 값으로 바뀝니다.
            """,
            security = @SecurityRequirement(name = REFRESH_COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "토큰 발급 성공",
            useReturnTypeSchema = true,
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = "새 HttpOnly Refresh 쿠키",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {"success":true,"data":{"accessToken":"<access-token>"},"error":null}
                    """)))
    @ApiResponse(responseCode = "401",
            description = "Refresh 쿠키가 없거나 현재 토큰이 유효하지 않습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403",
            description = "허용된 Origin에서 요청하지 않았거나 계정 상태 때문에 토큰을 발급할 수 없습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    ResponseEntity<BaseResponse<AccessTokenResponse>> refresh(
            @Parameter(hidden = true) @Nullable String refreshToken);

    @Operation(summary = "로그아웃", description = """
            허용된 Origin에서 브라우저의 `refresh_token` 쿠키를 보내면 서버가 토큰을 폐기하고 쿠키를 삭제합니다.
            같은 회원의 유효한 Access 토큰을 `Authorization: Bearer <token>`으로 함께 보내면 그 토큰도 만료될 때까지 사용할 수 없습니다.
            """,
            security = @SecurityRequirement(name = REFRESH_COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "로그아웃 성공",
            useReturnTypeSchema = true,
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Refresh 쿠키를 삭제하는 헤더",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {"success":true,"data":null,"error":null}
                    """)))
    @ApiResponse(responseCode = "401",
            description = "Refresh 쿠키가 없거나 현재 토큰이 유효하지 않습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403",
            description = "Origin이 없거나 허용 목록에 없는 주소입니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    ResponseEntity<BaseResponse<Void>> logout(
            @Parameter(hidden = true) HttpHeaders headers,
            @Parameter(hidden = true) @Nullable String refreshToken);
}
