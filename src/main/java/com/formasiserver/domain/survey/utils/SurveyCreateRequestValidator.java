package com.formasiserver.domain.survey.utils;

import com.formasiserver.domain.survey.dto.request.SurveyCreateRequest;
import com.formasiserver.domain.survey.entity.SurveyMethod;
import com.formasiserver.domain.survey.entity.SurveyPurposeCode;
import com.formasiserver.domain.user.entity.profile.ChildrenCount;
import com.formasiserver.domain.user.entity.profile.DriverLicense;
import com.formasiserver.domain.user.entity.profile.Field;
import com.formasiserver.domain.user.entity.profile.Gender;
import com.formasiserver.domain.user.entity.profile.HouseholdComposition;
import com.formasiserver.domain.user.entity.profile.HouseholdSize;
import com.formasiserver.domain.user.entity.profile.MaritalStatus;
import com.formasiserver.domain.user.entity.profile.Occupation;
import com.formasiserver.domain.user.entity.profile.PetExperience;
import com.formasiserver.domain.user.entity.profile.PetType;
import com.formasiserver.domain.user.entity.profile.ProfileOption;
import com.formasiserver.domain.user.entity.profile.Region;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.response.ErrorResponse.ValidationError;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class SurveyCreateRequestValidator {

    private static final Set<Integer> DURATIONS = Set.of(1, 2, 3, 5, 7, 10, 15, 20, 30, 40, 50, 60);
    private static final Map<String, ProfileOption[]> TARGET_OPTIONS = Map.ofEntries(
            Map.entry("GENDER", Gender.values()),
            Map.entry("OCCUPATION", Occupation.values()),
            Map.entry("FIELD", Field.values()),
            Map.entry("REGION", Region.values()),
            Map.entry("HOUSEHOLD_COMPOSITION", HouseholdComposition.values()),
            Map.entry("HOUSEHOLD_SIZE", HouseholdSize.values()),
            Map.entry("MARITAL_STATUS", MaritalStatus.values()),
            Map.entry("CHILDREN_COUNT", ChildrenCount.values()),
            Map.entry("PET_EXPERIENCE", PetExperience.values()),
            Map.entry("PET_TYPE", PetType.values()),
            Map.entry("DRIVER_LICENSE", DriverLicense.values())
    );

    private SurveyCreateRequestValidator() {
    }

    public static void validate(SurveyCreateRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        if (!isHttpsUrl(request.externalUrl())) {
            errors.add(new ValidationError("externalUrl", "https://로 시작하는 유효한 설문 주소를 입력해주세요."));
        }

        validateOtherText(errors, "purposeOther", request.purpose() == SurveyPurposeCode.OTHER, request.purposeOther());
        validateOtherText(errors, "methodOther", request.method() == SurveyMethod.OTHER, request.methodOther());

        int duration = request.durationMinutes();
        if (!DURATIONS.contains(duration)) {
            errors.add(new ValidationError("durationMinutes", "목록에서 소요 시간을 선택해주세요."));
        } else if ((long) request.targetCount() * duration > Integer.MAX_VALUE) {
            errors.add(new ValidationError("targetCount", "필요한 도움 시간이 저장 범위를 초과합니다."));
        }

        if (request.minAge() != null && request.maxAge() != null && request.minAge() > request.maxAge()) {
            errors.add(new ValidationError("minAge", "최소 나이는 최대 나이보다 클 수 없습니다."));
        }

        if (request.targetConditions() != null) {
            validateConditions(errors, request.targetConditions());
        }

        if (!errors.isEmpty()) {
            throw ApiException.validationFailed(errors);
        }
    }

    private static boolean isHttpsUrl(String link) {
        try {
            URI uri = URI.create(link);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validateOtherText(List<ValidationError> errors, String field, boolean otherSelected,
            @Nullable String text) {
        if (otherSelected && (text == null || text.isBlank())) {
            errors.add(new ValidationError(field, "기타를 선택한 경우 내용을 직접 입력해주세요."));
        } else if (!otherSelected && text != null) {
            errors.add(new ValidationError(field, "기타를 선택한 경우에만 입력할 수 있습니다."));
        }
    }

    private static void validateConditions(List<ValidationError> errors, Map<String, Set<String>> conditions) {
        for (var entry : conditions.entrySet()) {
            String item = entry.getKey();
            ProfileOption[] options = item == null ? null : TARGET_OPTIONS.get(item);
            Set<String> values = entry.getValue();
            if (options == null || values == null || values.stream()
                    .anyMatch(value -> Arrays.stream(options).noneMatch(option -> option.name().equals(value)))) {
                errors.add(new ValidationError("targetConditions", "대상 조건의 항목에 맞는 선택지를 골라주세요."));
                return;
            }
        }
    }
}
