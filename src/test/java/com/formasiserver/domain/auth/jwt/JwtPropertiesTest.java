package com.formasiserver.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtSettings.class)
            .withPropertyValues(
                    "auth.jwt.secret-key=test-key",
                    "auth.jwt.issuer=form-asi-server",
                    "auth.jwt.audience=form-asi-api",
                    "auth.jwt.access-ttl=30m"
            );

    @ParameterizedTest
    @CsvSource({
            "0s, JWT lifetimes must be positive whole seconds",
            "500ms, JWT lifetimes must be positive whole seconds",
            "15d, Refresh Token lifetime must not exceed 14 days"
    })
    void refusesToBindInvalidRefreshLifetimes(String ttl, String expectedMessage) {
        contextRunner.withPropertyValues("auth.jwt.refresh-ttl=" + ttl).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(expectedMessage);
        });
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtSettings {
    }
}
