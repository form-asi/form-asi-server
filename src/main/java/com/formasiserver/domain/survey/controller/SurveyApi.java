package com.formasiserver.domain.survey.controller;

import static com.formasiserver.global.config.SwaggerConfig.BEARER_AUTH;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.survey.dto.request.SurveyCreateRequest;
import com.formasiserver.domain.survey.dto.response.SurveyCreateResponse;
import com.formasiserver.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Survey", description = "설문을 등록하고 관리합니다.")
@SecurityRequirement(name = BEARER_AUTH)
public interface SurveyApi {

    @Operation(summary = "설문 공개 등록", description = """
            활성 회원이 설문을 바로 공개합니다. 필요한 도움 시간은 소요 시간과 목표 인원의 곱이며,
            유효한 가입 혜택 시간부터 차감됩니다.

            목적과 방식은 각각 하나씩 선택하고 `OTHER`를 선택한 항목의 직접 입력을 채워주세요.
            """)
    @ApiResponse(responseCode = "201", description = "설문 등록 성공", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "설문 입력값이 올바르지 않습니다.")
    @ApiResponse(responseCode = "401", description = "유효한 Access 토큰이 필요합니다.")
    @ApiResponse(responseCode = "403", description = "PROFILE_REQUIRED, ACCOUNT_SUSPENDED 또는 ACCOUNT_WITHDRAWN")
    @ApiResponse(responseCode = "409", description = "INSUFFICIENT_HELP_TIME. 사용 가능한 도움 시간이 모자라면 부족한 분 수를, "
            + "잔액이 마이너스면 설문을 올릴 수 없다는 안내를 반환합니다.")
    BaseResponse<SurveyCreateResponse> register(@Parameter(hidden = true) AuthenticatedUser principal,
            SurveyCreateRequest request);
}
