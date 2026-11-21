package com.formasiserver.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BirthDateValidatorTest {

    private final BirthDateValidator validator = new BirthDateValidator();

    @Test
    void rejectsUntilTheFourteenthBirthdayInKorea() {
        LocalDate birthDate = LocalDate.of(2012, 11, 19);

        assertRejected(birthDate, "2026-11-18T14:59:59Z", ErrorCode.UNDER_MINIMUM_AGE);
        assertThatCode(() -> validator.validate(birthDate, Instant.parse("2026-11-18T15:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsLeapDayBirthdaysFromMarchFirst() {
        LocalDate birthDate = LocalDate.of(2012, 2, 29);

        assertRejected(birthDate, "2026-02-28T14:59:59Z", ErrorCode.UNDER_MINIMUM_AGE);
        assertThatCode(() -> validator.validate(birthDate, Instant.parse("2026-02-28T15:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingAndFutureBirthDates() {
        String now = "2026-11-18T15:00:00Z";
        assertRejected(null, now, ErrorCode.BIRTH_DATE_UNAVAILABLE);
        assertRejected(LocalDate.of(2026, 11, 20), now, ErrorCode.BIRTH_DATE_UNAVAILABLE);
    }

    private void assertRejected(LocalDate birthDate, String now, ErrorCode errorCode) {
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> validator.validate(birthDate, Instant.parse(now)))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
