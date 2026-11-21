package com.formasiserver.domain.user.controller;

import static com.formasiserver.global.config.SwaggerConfig.BEARER_AUTH;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.user.dto.request.OnboardingRequest;
import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.dto.response.NicknameAvailabilityResponse;
import com.formasiserver.domain.user.dto.response.OnboardingResponse;
import com.formasiserver.domain.user.dto.response.ProfileOptionsResponse;
import com.formasiserver.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User", description = "내 정보를 확인하고 온보딩을 완료할 때 사용합니다.")
@SecurityRequirement(name = BEARER_AUTH)
@ApiResponse(responseCode = "401",
        description = "유효한 Access 토큰이 필요합니다.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
public interface UserApi {

    @Operation(summary = "내 정보 조회", description = """
            로그인한 회원의 ID, 닉네임, 나이, 상태를 확인할 수 있습니다.
            `status`가 `PENDING`이면 온보딩을 진행하세요. 아직 생년월일이 저장되지 않았다면 `age`는 `null`입니다.
            """)
    @ApiResponse(responseCode = "200", description = "내 정보 조회 성공",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "온보딩 전", value = """
                    {"success":true,"data":{"userId":"e7cc72fc-8b37-44eb-a154-051f5652a9bd","nickname":null,
                    "age":null,"status":"PENDING"},"error":null}
                    """)))
    @ApiResponse(responseCode = "403", description = "정지 또는 탈퇴한 계정",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    BaseResponse<CurrentUserResponse> me(@Parameter(hidden = true) AuthenticatedUser principal);

    @Operation(summary = "프로필 선택지 조회",
            description = "온보딩 화면에 보여줄 선택지입니다. 화면에는 `label`을 표시하고, 선택한 값은 `code`로 보내세요.")
    @ApiResponse(responseCode = "200", description = "프로필 선택지 조회 성공",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", description = "정지 또는 탈퇴한 계정",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    BaseResponse<ProfileOptionsResponse> profileOptions();

    @Operation(summary = "닉네임 중복 확인",
            description = """
                    사용하려는 닉네임을 확인합니다. 한글·영문·숫자로 2~10자를 입력하세요.
                    영문 대소문자만 다른 닉네임은 같은 이름으로 봅니다.
                    """)
    @ApiResponse(responseCode = "200", description = "닉네임 사용 가능 여부",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {"success":true,"data":{"available":true},"error":null}
                    """)))
    @ApiResponse(responseCode = "400", description = "닉네임이 비어 있거나 형식이 올바르지 않습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "정지 또는 탈퇴한 계정",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    BaseResponse<NicknameAvailabilityResponse> nicknameAvailability(
            @Parameter(description = "확인할 닉네임", example = "폼앗이24") String nickname);

    @Operation(summary = "온보딩 완료", description = """
            닉네임과 프로필을 제출하면 회원 상태가 `ACTIVE`로 바뀝니다.
            생년월일을 처음 입력한다면 양력 `YYYY-MM-DD` 형식으로 보내세요.

            - 분야는 하나 이상 선택하세요.
            - 직업·분야·가구 구성·결혼 여부에서 `OTHER`를 고르면 해당 기타 입력란도 채워주세요.
            - 반려동물 종류는 예전에 키웠거나 지금 키우고 있을 때 선택할 수 있습니다.
            """)
    @ApiResponse(responseCode = "200", description = "온보딩 완료",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {"success":true,"data":{"userId":"e7cc72fc-8b37-44eb-a154-051f5652a9bd","nickname":"폼앗이24",
                    "status":"ACTIVE"},"error":null}
                    """)))
    @ApiResponse(responseCode = "400", description = "필수 항목이 비어 있거나 입력이 올바르지 않습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403",
            description = "만 14세 미만이거나 계정 상태 때문에 온보딩을 마칠 수 없습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임이거나 온보딩을 완료한 계정입니다.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BaseResponse.class),
                    examples = {
                            @ExampleObject(name = "닉네임 중복", value = """
                                    {"success":false,"data":null,"error":{"code":"NICKNAME_ALREADY_IN_USE",
                                    "message":"이미 사용 중인 닉네임입니다.","errors":[]}}
                                    """),
                            @ExampleObject(name = "이미 완료", value = """
                                    {"success":false,"data":null,"error":{"code":"ONBOARDING_ALREADY_COMPLETED",
                                    "message":"이미 프로필 입력을 완료했습니다.","errors":[]}}
                                    """)
                    }))
    BaseResponse<OnboardingResponse> completeOnboarding(
            @Parameter(hidden = true) AuthenticatedUser principal,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OnboardingRequest.class),
                            examples = @ExampleObject(name = "신규 회원", value = """
                                    {"nickname":"폼앗이24","birthDate":"2000-01-01","gender":"FEMALE",
                                    "occupation":"EMPLOYEE","fields":["DESIGN"]}
                                    """)))
            OnboardingRequest request);
}
