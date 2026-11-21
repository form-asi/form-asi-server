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
import com.formasiserver.domain.user.entity.profile.ProfileOption;
import com.formasiserver.domain.user.entity.profile.Region;
import java.util.Arrays;
import java.util.List;

public record ProfileOptionsResponse(
        List<Choice> genders,
        List<Choice> occupations,
        List<Choice> fields,
        List<Choice> regions,
        List<Choice> householdCompositions,
        List<Choice> householdSizes,
        List<Choice> maritalStatuses,
        List<Choice> childrenCounts,
        List<Choice> petExperiences,
        List<Choice> petTypes,
        List<Choice> driverLicenses
) {

    public static ProfileOptionsResponse fromOptions() {
        return new ProfileOptionsResponse(
                choices(Gender.values()),
                choices(Occupation.values()),
                choices(Field.values()),
                choices(Region.values()),
                choices(HouseholdComposition.values()),
                choices(HouseholdSize.values()),
                choices(MaritalStatus.values()),
                choices(ChildrenCount.values()),
                choices(PetExperience.values()),
                choices(PetType.values()),
                choices(DriverLicense.values())
        );
    }

    private static List<Choice> choices(ProfileOption[] options) {
        return Arrays.stream(options)
                .map(option -> new Choice(option.name(), option.getLabel()))
                .toList();
    }

    public record Choice(String code, String label) {
    }
}
