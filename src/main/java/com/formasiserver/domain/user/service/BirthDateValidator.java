package com.formasiserver.domain.user.service;

import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class BirthDateValidator {

    private static final int MINIMUM_AGE = 14;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public void validate(@Nullable LocalDate birthDate) {
        validate(birthDate, Instant.now());
    }

    public int age(LocalDate birthDate) {
        return age(birthDate, LocalDate.now(KOREA_ZONE));
    }

    void validate(@Nullable LocalDate birthDate, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, KOREA_ZONE);
        if (age(birthDate, today) < MINIMUM_AGE) {
            throw new ApiException(ErrorCode.UNDER_MINIMUM_AGE);
        }
    }

    private static int age(@Nullable LocalDate birthDate, LocalDate today) {
        if (birthDate == null || birthDate.getYear() < 1 || birthDate.isAfter(today)) {
            throw new ApiException(ErrorCode.BIRTH_DATE_UNAVAILABLE);
        }
        // A February 29 birthday reaches the next whole year on March 1 in a non-leap year.
        return Math.toIntExact(ChronoUnit.YEARS.between(birthDate, today));
    }
}
