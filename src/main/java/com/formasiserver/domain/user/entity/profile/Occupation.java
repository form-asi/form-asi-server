package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Occupation implements ProfileOption {

    SECONDARY_SCHOOL_STUDENT("중/고등학생"),
    UNDERGRADUATE("대학생"),
    GRADUATE_STUDENT("대학원생"),
    EMPLOYEE("직장인"),
    SELF_EMPLOYED("자영업/개인사업자(법인 제외)"),
    FREELANCER("프리랜서"),
    JOB_SEEKER("취업준비생"),
    HOMEMAKER("주부"),
    MILITARY("군인"),
    UNEMPLOYED("무직"),
    ON_LEAVE("휴직"),
    OTHER("기타(직접 입력)");

    private final String label;
}
