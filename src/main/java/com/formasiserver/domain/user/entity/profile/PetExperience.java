package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PetExperience implements ProfileOption {

    NEVER("키운 적 없음"),
    PAST("과거에 키움"),
    CURRENT("현재 키우고 있음");

    private final String label;
}
