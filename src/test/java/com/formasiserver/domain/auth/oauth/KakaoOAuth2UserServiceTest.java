package com.formasiserver.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.formasiserver.global.exception.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class KakaoOAuth2UserServiceTest {

    @Test
    void mapsOnlyTheKakaoId() {
        KakaoOAuth2User user = KakaoOAuth2UserService.mapUser(Map.of("id", 123456789012L,
                "kakao_account", Map.of("birthyear", "2012", "birthday", "0229")));
        assertThat(user.getName()).isEqualTo("123456789012");
        assertThat(user.getAttributes()).containsOnlyKeys("id");
    }

    @Test
    void rejectsAnInvalidKakaoId() {
        assertThatExceptionOfType(OAuth2AuthenticationException.class)
                .isThrownBy(() -> KakaoOAuth2UserService.mapUser(Map.of("id", 1.5d)))
                .satisfies(exception -> assertThat(exception.getError().getErrorCode())
                        .isEqualTo(ErrorCode.KAKAO_AUTH_UNAVAILABLE.getCode()));
    }

}
