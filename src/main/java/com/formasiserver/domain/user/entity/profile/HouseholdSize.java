package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HouseholdSize implements ProfileOption {

    ONE("1명"),
    TWO("2명"),
    THREE("3명"),
    FOUR("4명"),
    FIVE_OR_MORE("5명 이상");

    private final String label;
}
