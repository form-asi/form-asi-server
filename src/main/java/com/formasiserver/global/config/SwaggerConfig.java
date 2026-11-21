package com.formasiserver.global.config;

import com.formasiserver.domain.auth.security.RefreshTokenCookieFactory;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String REFRESH_COOKIE_AUTH = "refreshCookie";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("폼앗이 API")
                        .description("""
                                1. 브라우저에서 [카카오 로그인](/api/v1/auth/login/kakao)을 진행하세요.
                                   로그인 후 서버가 `/login/oauth2/code/kakao`에서 콜백을 처리하고 Refresh 쿠키를 설정합니다.
                                2. **토큰 재발급**을 실행해 응답에서 `data.accessToken`을 복사하세요.
                                3. 화면 위쪽의 **Authorize**에 복사한 토큰을 입력하면 회원 API를 호출할 수 있습니다.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(REFRESH_COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(RefreshTokenCookieFactory.NAME)
                                .description("카카오 로그인에 성공하면 브라우저에 이 쿠키가 저장됩니다.")));
    }

}
