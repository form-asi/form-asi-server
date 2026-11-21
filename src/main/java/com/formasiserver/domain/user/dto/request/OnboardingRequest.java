package com.formasiserver.domain.user.dto.request;

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
import com.formasiserver.domain.user.entity.profile.Region;
import com.formasiserver.domain.user.utils.NicknameUtils;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record OnboardingRequest(
        @NotNull(message = "닉네임을 입력해주세요.")
        @Pattern(regexp = NicknameUtils.FORMAT, message = NicknameUtils.FORMAT_MESSAGE)
        String nickname,
        @NotNull(message = "성별을 선택해주세요.")
        Gender gender,
        @NotNull(message = "직업을 선택해주세요.")
        Occupation occupation,
        @Nullable String occupationOther,
        @NotEmpty(message = "분야를 한 개 이상 선택해주세요.")
        List<@NotNull(message = "분야를 선택해주세요.") Field> fields,
        @Nullable String fieldOther,
        @Nullable Region region,
        @Nullable HouseholdComposition householdComposition,
        @Nullable String householdCompositionOther,
        @Nullable HouseholdSize householdSize,
        @Nullable MaritalStatus maritalStatus,
        @Nullable String maritalStatusOther,
        @Nullable ChildrenCount childrenCount,
        @Nullable PetExperience petExperience,
        @Nullable List<@NotNull(message = "반려동물 종류를 선택해주세요.") PetType> pets,
        @Nullable DriverLicense driverLicense
) {
}
