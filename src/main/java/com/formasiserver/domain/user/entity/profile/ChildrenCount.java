package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChildrenCount implements ProfileOption {

    NONE("없음"),
    ONE("1명"),
    TWO("2명"),
    THREE("3명"),
    FOUR_OR_MORE("4명 이상");

    private final String label;
}
