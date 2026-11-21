package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DriverLicense implements ProfileOption {

    NONE("없음"),
    CLASS_1("제1종 면허 보유"),
    CLASS_2("제2종 면허 보유");

    private final String label;
}
