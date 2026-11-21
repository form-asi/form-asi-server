package com.formasiserver.domain.auth.oauth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@RequiredArgsConstructor
public final class KakaoOAuth2User implements OAuth2User {

    private final String kakaoId;

    @Override
    public String getName() {
        return kakaoId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("id", kakaoId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
    }

}
