package com.formasiserver.domain.user.controller;

import static com.formasiserver.global.config.SwaggerConfig.BEARER_AUTH;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.helptime.dto.request.HelpTimeFilter;
import com.formasiserver.domain.helptime.dto.response.HelpTimeHistoryResponse;
import com.formasiserver.domain.user.dto.request.OnboardingRequest;
import com.formasiserver.domain.user.dto.request.ProfileUpdateRequest;
import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.dto.response.NicknameAvailabilityResponse;
import com.formasiserver.domain.user.dto.response.OnboardingResponse;
import com.formasiserver.domain.user.dto.response.ProfileOptionsResponse;
import com.formasiserver.domain.user.dto.response.UserProfileResponse;
import com.formasiserver.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User", description = "내 정보를 확인하고 프로필을 등록하거나 수정할 때 사용합니다.")
@SecurityRequirement(name = BEARER_AUTH)
@ApiResponse(responseCode = "401",
        description = "유효한 Access 토큰이 필요합니다.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
public interface UserApi {

    @Operation(summary = "내 정보 조회", description = """
            로그인한 회원의 ID, 닉네임, 나이, 상태, 현재 도움 시간을 확인할 수 있습니다.
            `status`가 `PENDING`이면 온보딩을 진행하세요. 아직 생년월일이 저장되지 않았다면 `age`는 `null`입니다.
            `helpTimeMinutes`는 분 단위 잔액입니다. 시간 계정이 없으면 `0`이며 잔액이 음수면 그대로 반환합니다.
            """)
    @ApiResponse(responseCode = "200", description = "내 정보 조회 성공",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "온보딩 전", value = """
                    {"success":true,"data":{"userId":"e7cc72fc-8b37-44eb-a154-051f5652a9bd","nickname":null,
                    "age":null,"status":"PENDING","helpTimeMinutes":0},"error":null}
                    """)))
    @ApiResponse(responseCode = "403", description = "정지 또는 탈퇴한 계정",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    BaseResponse<CurrentUserResponse> me(@Parameter(hidden = true) AuthenticatedUser principal);

    @Operation(summary = "내 도움 시간 상세 조회", description = """
            현재 잔액과 도움 시간 내역을 최신순으로 반환합니다.
            필터는 변동량 부호에 따라 적용됩니다. 각 항목의 `balanceAfter`는 거래 직후 잔액입니다.
            `expiresAt`은 가입 혜택 지급 항목의 만료 시각입니다.
            """)
    @ApiResponse(responseCode = "200", description = "도움 시간 조회 성공", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "올바르지 않은 필터")
    @ApiResponse(responseCode = "403", description = "온보딩 전이거나 정지 또는 탈퇴한 계정")
    BaseResponse<HelpTimeHistoryResponse> myHelpTime(@Parameter(hidden = true) AuthenticatedUser principal,
            @Parameter(description = "전체, 적립, 차감", example = "ALL") HelpTimeFilter filter);

    @Operation(summary = "내 프로필 상세 조회", description = """
            온보딩을 완료한 회원이 현재 저장된 프로필을 확인할 수 있습니다.
            선택 항목에 저장된 값이 없으면 `null`을, 선택한 반려동물 종류가 없으면 빈 배열을 반환합니다.
            나이는 생년월일을 기준으로 계산한 만 나이입니다.
            """)
    @ApiResponse(responseCode = "200", description = "내 프로필 상세 조회 성공", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", description = "온보딩 전이거나 정지 또는 탈퇴한 계정",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "저장된 프로필을 찾을 수 없습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    BaseResponse<UserProfileResponse> myProfile(@Parameter(hidden = true) AuthenticatedUser principal);

    @Operation(summary = "내 프로필 수정", description = """
            온보딩을 완료한 회원이 닉네임과 프로필을 수정할 수 있습니다.
            필수 항목과 유지할 선택 항목을 함께 보내세요. 생략하거나 `null`로 보낸 선택 항목은 비워집니다.
            생년월일과 나이는 변경할 수 없습니다.
            """)
    @ApiResponse(responseCode = "200", description = "저장된 프로필", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "필수 항목이 비어 있거나 입력이 올바르지 않습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "온보딩 전이거나 정지 또는 탈퇴한 계정",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "저장된 프로필을 찾을 수 없습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임입니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class)))
    BaseResponse<UserProfileResponse> updateMyProfile(@Parameter(hidden = true) AuthenticatedUser principal,
            ProfileUpdateRequest request);

    @Operation(summary = "프로필 선택지 조회",
            description = "프로필 화면에 보여줄 선택지입니다. 화면에는 `label`을 표시하고, 선택한 값은 `code`로 보내세요.")
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
