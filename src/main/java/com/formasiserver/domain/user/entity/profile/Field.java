package com.formasiserver.domain.user.entity.profile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Field implements ProfileOption {

    BUSINESS_PLANNING("경영·사업기획"),
    MARKETING("마케팅·광고·홍보"),
    DESIGN("디자인"),
    SOFTWARE_DEVELOPMENT("IT·소프트웨어 개발"),
    DATA_AI("데이터 분석·AI"),
    HUMANITIES_SOCIAL_SCIENCES("인문·사회·심리학"),
    LAW("법률·법학"),
    EDUCATION("교육·교육학"),
    ENGINEERING_NATURAL_SCIENCES("공학·자연과학"),
    MEDICINE_HEALTH("의약·보건"),
    ARTS_SPORTS("예술·체육"),
    OTHER("기타(직접 입력)");

    private final String label;
}
