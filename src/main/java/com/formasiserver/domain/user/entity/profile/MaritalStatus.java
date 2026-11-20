package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MaritalStatus implements ProfileOption {

    UNMARRIED("미혼"),
    MARRIED("기혼"),
    DIVORCED("이혼"),
    OTHER("기타(직접 입력)");

    private final String label;
}
