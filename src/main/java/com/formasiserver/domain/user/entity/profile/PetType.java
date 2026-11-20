package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PetType implements ProfileOption {

    DOG("강아지"),
    CAT("고양이"),
    OTHER_MAMMAL("기타 포유류"),
    BIRD("조류"),
    AMPHIBIAN("양서류"),
    REPTILE("파충류"),
    INSECT("곤충류"),
    PLANT("식물"),
    OTHER("기타");

    private final String label;
}
