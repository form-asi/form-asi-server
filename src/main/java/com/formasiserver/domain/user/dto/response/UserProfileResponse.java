package com.formasiserver.domain.user.dto.response;

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
import java.util.List;
import org.jspecify.annotations.Nullable;

public record UserProfileResponse(
        @Nullable String nickname,
        @Nullable Integer age,
        Gender gender,
        Occupation occupation,
        @Nullable String occupationOther,
        List<Field> fields,
        @Nullable String fieldOther,
        @Nullable Region region,
        @Nullable HouseholdComposition householdComposition,
        @Nullable String householdCompositionOther,
        @Nullable HouseholdSize householdSize,
        @Nullable MaritalStatus maritalStatus,
        @Nullable String maritalStatusOther,
        @Nullable ChildrenCount childrenCount,
        @Nullable PetExperience petExperience,
        List<PetType> pets,
        @Nullable DriverLicense driverLicense
) {
}
