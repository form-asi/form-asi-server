package com.formasiserver.domain.user.service;

import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class BirthDateValidator {

    private static final int MINIMUM_AGE = 14;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public void validate(LocalDate birthDate) {
        validate(birthDate, Instant.now());
    }

    void validate(LocalDate birthDate, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, KOREA_ZONE);
        if (birthDate == null || birthDate.isAfter(today)) {
            throw new ApiException(ErrorCode.BIRTH_DATE_UNAVAILABLE);
        }

        LocalDate eligibleFrom = birthDate.plusYears(MINIMUM_AGE);
        if (eligibleFrom.getDayOfMonth() != birthDate.getDayOfMonth()) {
            eligibleFrom = eligibleFrom.plusDays(1);
        }
        if (today.isBefore(eligibleFrom)) {
            throw new ApiException(ErrorCode.UNDER_MINIMUM_AGE);
        }
    }
}
