package com.formasiserver.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class KakaoOAuth2UserServiceTest {

    @Test
    void mapsTheKakaoIdAndSolarBirthDate() {
        KakaoOAuth2User user = userWithAccount(Map.of(
                "birthyear", "2012", "birthday", "0229", "birthday_type", "SOLAR"));

        assertThat(user.getName()).isEqualTo("123456789012");
        assertThat(user.getAttributes()).containsOnlyKeys("id");
        assertThat(user.requireBirthDate()).isEqualTo(LocalDate.of(2012, 2, 29));
    }

    @Test
    void convertsRegularAndLeapLunarMonthsToDifferentSolarDates() {
        // KASI almanac lists 2012-03-22 and 2012-04-21 for the regular and leap months.
        KakaoOAuth2User regular = userWithAccount(Map.of(
                "birthyear", "2012", "birthday", "0301", "birthday_type", "LUNAR", "is_leap_month", false));
        KakaoOAuth2User leap = userWithAccount(Map.of(
                "birthyear", "2012", "birthday", "0301", "birthday_type", "LUNAR", "is_leap_month", true));

        assertThat(regular.requireBirthDate()).isEqualTo(LocalDate.of(2012, 3, 22));
        assertThat(leap.requireBirthDate()).isEqualTo(LocalDate.of(2012, 4, 21));
    }

    @Test
    void requestsVerificationWhenTheLunarMonthIsAmbiguous() {
        assertBirthError(Map.of("birthyear", "2012", "birthday", "0301", "birthday_type", "LUNAR"),
                ErrorCode.BIRTH_DATE_VERIFICATION_REQUIRED);
    }

    @Test
    void reportsConsentAndInvalidBirthDataWhenTheDateIsRequired() {
        assertBirthError(Map.of("birthyear_needs_agreement", true), ErrorCode.BIRTH_DATE_AGREEMENT_REQUIRED);
        assertBirthError(Map.of(), ErrorCode.BIRTH_DATE_UNAVAILABLE);
        assertBirthError(Map.of("birthyear", "2011", "birthday", "0229", "birthday_type", "SOLAR"),
                ErrorCode.BIRTH_DATE_UNAVAILABLE);
        assertBirthError(Map.of("birthyear", "2012", "birthday", "0301"), ErrorCode.BIRTH_DATE_UNAVAILABLE);
    }

    @Test
    void rejectsAnInvalidKakaoId() {
        assertThatExceptionOfType(OAuth2AuthenticationException.class)
                .isThrownBy(() -> KakaoOAuth2UserService.mapUser(Map.of("id", 1.5d)))
                .satisfies(exception -> assertThat(exception.getError().getErrorCode())
                        .isEqualTo(ErrorCode.KAKAO_AUTH_UNAVAILABLE.getCode()));
    }

    private static KakaoOAuth2User userWithAccount(Map<String, Object> account) {
        return KakaoOAuth2UserService.mapUser(Map.of("id", 123456789012L, "kakao_account", account));
    }

    private static void assertBirthError(Map<String, Object> account, ErrorCode errorCode) {
        KakaoOAuth2User user = userWithAccount(account);
        assertThat(user.getName()).isEqualTo("123456789012");
        assertThatExceptionOfType(ApiException.class).isThrownBy(user::requireBirthDate)
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
