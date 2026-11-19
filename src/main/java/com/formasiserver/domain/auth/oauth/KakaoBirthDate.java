package com.formasiserver.domain.auth.oauth;

import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import net.time4j.PlainDate;
import net.time4j.calendar.EastAsianMonth;
import net.time4j.calendar.EastAsianYear;
import net.time4j.calendar.KoreanCalendar;

public record KakaoBirthDate(String year, String monthAndDay, String calendarType, Boolean leapMonth,
                             boolean agreementRequired) {

    public static KakaoBirthDate from(Map<String, Object> attributes) {
        Map<?, ?> account = attributes.get("kakao_account") instanceof Map<?, ?> value ? value : Map.of();
        return new KakaoBirthDate(
                stringValue(account.get("birthyear")),
                stringValue(account.get("birthday")),
                stringValue(account.get("birthday_type")),
                account.get("is_leap_month") instanceof Boolean value ? value : null,
                Boolean.TRUE.equals(account.get("birthyear_needs_agreement"))
                        || Boolean.TRUE.equals(account.get("birthday_needs_agreement")));
    }

    public LocalDate requireSolarDate() {
        if (agreementRequired) {
            throw new ApiException(ErrorCode.BIRTH_DATE_AGREEMENT_REQUIRED);
        }
        if (!year.matches("[0-9]{4}") || !monthAndDay.matches("[0-9]{4}") || year.equals("0000")) {
            throw new ApiException(ErrorCode.BIRTH_DATE_UNAVAILABLE);
        }

        int birthYear = Integer.parseInt(year);
        int month = Integer.parseInt(monthAndDay.substring(0, 2));
        int day = Integer.parseInt(monthAndDay.substring(2));
        try {
            return switch (calendarType) {
                case "SOLAR" -> LocalDate.of(birthYear, month, day);
                case "LUNAR" -> convertLunarDate(birthYear, month, day);
                default -> throw new ApiException(ErrorCode.BIRTH_DATE_UNAVAILABLE);
            };
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.BIRTH_DATE_UNAVAILABLE);
        }
    }

    private LocalDate convertLunarDate(int year, int month, int day) {
        EastAsianYear lunarYear = EastAsianYear.forGregorian(year);
        EastAsianMonth lunarMonth = EastAsianMonth.valueOf(month);
        if (leapMonth == null && KoreanCalendar.of(lunarYear, lunarMonth, 1)
                .findLeapMonth().filter(lunarMonth.withLeap()::equals).isPresent()) {
            throw new ApiException(ErrorCode.BIRTH_DATE_VERIFICATION_REQUIRED);
        }
        if (Boolean.TRUE.equals(leapMonth)) {
            lunarMonth = lunarMonth.withLeap();
        }
        return KoreanCalendar.of(lunarYear, lunarMonth, day).transform(PlainDate.class).toTemporalAccessor();
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : "";
    }
}
