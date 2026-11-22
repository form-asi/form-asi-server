package com.formasiserver.domain.user.utils;

import com.formasiserver.domain.user.dto.request.ProfileUpdateRequest;
import com.formasiserver.domain.user.entity.profile.Field;
import com.formasiserver.domain.user.entity.profile.HouseholdComposition;
import com.formasiserver.domain.user.entity.profile.MaritalStatus;
import com.formasiserver.domain.user.entity.profile.Occupation;
import com.formasiserver.domain.user.entity.profile.PetExperience;
import com.formasiserver.domain.user.entity.profile.PetType;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.response.ErrorResponse.ValidationError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ProfileOptionsValidator {

    private static final int OTHER_TEXT_MAX_LENGTH = 20;

    private ProfileOptionsValidator() {
    }

    public static void validate(ProfileUpdateRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        validateOtherText(errors, "occupationOther", request.occupation() == Occupation.OTHER, request.occupationOther());
        validateOtherText(errors, "fieldOther", request.fields().contains(Field.OTHER), request.fieldOther());
        validateOtherText(errors, "householdCompositionOther",
                request.householdComposition() == HouseholdComposition.OTHER, request.householdCompositionOther());
        validateOtherText(errors, "maritalStatusOther",
                request.maritalStatus() == MaritalStatus.OTHER, request.maritalStatusOther());

        if (new HashSet<>(request.fields()).size() != request.fields().size()) {
            errors.add(new ValidationError("fields", "같은 분야를 여러 번 선택할 수 없습니다."));
        }

        List<PetType> pets = request.pets();
        if (pets != null && !pets.isEmpty()) {
            if (request.petExperience() != PetExperience.PAST && request.petExperience() != PetExperience.CURRENT) {
                errors.add(new ValidationError("pets", "반려동물을 키운 경험이 있을 때만 종류를 선택할 수 있습니다."));
            } else if (new HashSet<>(pets).size() != pets.size()) {
                errors.add(new ValidationError("pets", "같은 반려동물 종류를 여러 번 선택할 수 없습니다."));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validationFailed(errors);
        }
    }

    private static void validateOtherText(List<ValidationError> errors, String field, boolean selected,
            @Nullable String text) {
        if (selected) {
            if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > OTHER_TEXT_MAX_LENGTH) {
                errors.add(new ValidationError(field, "기타 입력은 공백 포함 20자 이내로 입력해주세요."));
            }
        } else if (text != null) {
            errors.add(new ValidationError(field, "기타를 선택한 경우에만 입력할 수 있습니다."));
        }
    }
}
