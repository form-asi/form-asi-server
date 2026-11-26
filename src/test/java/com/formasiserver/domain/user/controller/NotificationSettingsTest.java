package com.formasiserver.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationSettingsTest {

    private static final String ME = "/api/v1/users/me";
    private static final String SETTINGS = ME + "/notification-settings";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private AuthTokenRedisRepository tokenRepository;

    @Test
    void storesOnlyTheSignedInMembersSettingAndShowsItInMyInfo() throws Exception {
        UUID userId = member(UserStatus.ACTIVE);
        UUID otherId = member(UserStatus.ACTIVE);
        String access = access(userId);

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationEnabled").value(true));

        mvc.perform(update(access, """
                {"notificationEnabled":false}
                """).param("userId", otherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationEnabled").value(false));
        entityManager.flush();
        assertThat(setting(userId)).isFalse();
        assertThat(setting(otherId)).isTrue();

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationEnabled").value(false));

        mvc.perform(update(access, """
                {"notificationEnabled":true}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationEnabled").value(true));
        entityManager.flush();
        assertThat(setting(userId)).isTrue();
    }

    @Test
    void rejectsAnOmittedSettingInsteadOfTurningItOff() throws Exception {
        UUID userId = member(UserStatus.ACTIVE);

        mvc.perform(update(access(userId), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value("notificationEnabled"));
        assertThat(setting(userId)).isTrue();
    }

    @Test
    void requiresAnAuthenticatedActiveMember() throws Exception {
        String body = """
                {"notificationEnabled":false}
                """;
        mvc.perform(patch(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        UUID pending = member(UserStatus.PENDING);
        mvc.perform(update(access(pending), body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROFILE_REQUIRED"));
        assertThat(setting(pending)).isTrue();
    }

    private UUID member(UserStatus status) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (user_id, kakao_id, birth_date, role, status, created_at, updated_at)
                VALUES (?, ?, DATE '2000-01-01', 'USER', ?, now(), now())
                """, userId, userId.toString(), status.name());
        return userId;
    }

    private Boolean setting(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT notification_enabled FROM users WHERE user_id = ?",
                Boolean.class, userId);
    }

    private String access(UUID userId) {
        return "Bearer " + jwtProvider.issueAccessToken(userId).value();
    }

    private static MockHttpServletRequestBuilder update(String access, String body) {
        return patch(SETTINGS).header(HttpHeaders.AUTHORIZATION, access)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
