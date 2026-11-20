package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HouseholdComposition implements ProfileOption {

    LIVING_ALONE("1인 가구(자취)"),
    WITH_PARENTS("부모님과 거주"),
    WITH_SPOUSE_ONLY("배우자와 거주(자녀 없음)"),
    WITH_SPOUSE_AND_CHILDREN("배우자와 자녀와 거주"),
    WITH_CHILDREN_ONLY("자녀와 거주(배우자 없음)"),
    WITH_SIBLINGS("형제·자매와 거주"),
    WITH_EXTENDED_FAMILY("조부모·친척과 거주"),
    WITH_ROOMMATES("친구·지인·룸메이트와 거주"),
    OTHER("기타(직접 입력)");

    private final String label;
}
