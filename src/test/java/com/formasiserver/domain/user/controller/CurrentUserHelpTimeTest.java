package com.formasiserver.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrentUserHelpTimeTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private AuthTokenRedisRepository tokenRepository;

    @Test
    void readsOnlyTheSignedInMembersCombinedHelpTimeBalance() throws Exception {
        UUID userId = newActiveMember();
        UUID otherId = newActiveMember();
        String access = "Bearer " + jwtProvider.issueAccessToken(userId).value();
        insertAccount(otherId, 80, 15);

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, access)
                        .param("userId", otherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpTimeMinutes").value(0));

        insertAccount(userId, -25, 15);
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpTimeMinutes").value(-10));
    }

    private UUID newActiveMember() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (user_id, kakao_id, birth_date, role, status, created_at, updated_at)
                VALUES (?, ?, DATE '2000-01-01', 'USER', 'ACTIVE', now(), now())
                """, userId, userId.toString());
        return userId;
    }

    private void insertAccount(UUID userId, int balance, int bonusBalance) {
        jdbcTemplate.update("""
                INSERT INTO time_account (user_id, balance, bonus_balance, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, userId, balance, bonusBalance);
    }
}
