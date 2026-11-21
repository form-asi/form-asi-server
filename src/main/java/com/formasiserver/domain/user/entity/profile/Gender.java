package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Gender implements ProfileOption {

    MALE("남"),
    FEMALE("여");

    private final String label;
}
