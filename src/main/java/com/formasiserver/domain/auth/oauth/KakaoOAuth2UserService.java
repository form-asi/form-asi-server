package com.formasiserver.domain.auth.oauth;

import com.formasiserver.global.exception.ErrorCode;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public final class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    private static final OAuth2Error KAKAO_AUTH_UNAVAILABLE = new OAuth2Error(ErrorCode.KAKAO_AUTH_UNAVAILABLE.getCode());

    public KakaoOAuth2UserService(JdkClientHttpRequestFactory kakaoRequestFactory) {
        var restTemplate = new RestTemplate(kakaoRequestFactory);
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        setRestOperations(restTemplate);
    }

    @Override
    public KakaoOAuth2User loadUser(OAuth2UserRequest request) {
        Map<String, Object> attributes;
        try {
            attributes = super.loadUser(request).getAttributes();
        } catch (OAuth2AuthenticationException exception) {
            throw new OAuth2AuthenticationException(KAKAO_AUTH_UNAVAILABLE);
        }
        return mapUser(attributes);
    }

    public static KakaoOAuth2User mapUser(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (!(id instanceof Integer || id instanceof Long) || ((Number) id).longValue() <= 0) {
            throw new OAuth2AuthenticationException(KAKAO_AUTH_UNAVAILABLE);
        }
        return new KakaoOAuth2User(id.toString(), KakaoBirthDate.from(attributes));
    }

}
