package com.formasiserver.domain.survey.dto.request;

import com.formasiserver.domain.survey.entity.SurveyMethod;
import com.formasiserver.domain.survey.entity.SurveyPurposeCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.constraints.CodePointLength;
import org.jspecify.annotations.Nullable;

public record SurveyCreateRequest(
        @NotBlank(message = "외부 설문 링크를 입력해주세요.")
        String externalUrl,
        @NotNull(message = "설문 목적을 선택해주세요.")
        SurveyPurposeCode purpose,
        @Nullable @CodePointLength(max = 100, message = "기타 설문 목적은 공백 포함 100자 이내로 입력해주세요.")
        String purposeOther,
        @NotNull(message = "설문 방식을 선택해주세요.")
        SurveyMethod method,
        @Nullable @CodePointLength(max = 100, message = "기타 설문 방식은 공백 포함 100자 이내로 입력해주세요.")
        String methodOther,
        @NotBlank(message = "설문 제목을 입력해주세요.")
        @CodePointLength(max = 100, message = "설문 제목은 공백 포함 100자 이내로 입력해주세요.")
        String title,
        @NotNull(message = "목표 인원을 입력해주세요.")
        @Positive(message = "목표 인원은 1명 이상이어야 합니다.")
        Integer targetCount,
        @NotNull(message = "소요 시간을 선택해주세요.")
        Integer durationMinutes,
        @Nullable @Min(14) @Max(100) Integer minAge,
        @Nullable @Min(14) @Max(100) Integer maxAge,
        @Nullable Map<String, Set<String>> targetConditions,
        @Nullable @CodePointLength(max = 200, message = "대상 설명은 공백 포함 200자 이내로 입력해주세요.")
        String targetDescription,
        @Nullable @CodePointLength(max = 700, message = "설문 소개는 공백 포함 700자 이내로 입력해주세요.")
        String introduction,
        @Nullable @CodePointLength(max = 100, message = "보상 설명은 공백 포함 100자 이내로 입력해주세요.")
        String rewardDescription
) {
}
