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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrentUserHelpTimeTest {

    private static final String HELP_TIME = "/api/v1/users/me/help-time";

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

    @Test
    void returnsAnEmptyHistoryWhenTheSignedInMemberHasNoTimeAccount() throws Exception {
        UUID userId = newActiveMember();

        mvc.perform(get(HELP_TIME)).andExpect(status().isUnauthorized());
        mvc.perform(helpTime(userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpTimeMinutes").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(helpTime(userId).param("filter", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersAndSortsOnlyTheSignedInMembersLedgerEntries() throws Exception {
        UUID userId = newActiveMember();
        UUID otherId = newActiveMember();
        insertAccount(userId, -10, 0);
        insertAccount(otherId, 80, 0);
        jdbcTemplate.update("""
                INSERT INTO signup_bonus (user_id, granted_minutes, granted_at, expires_at, created_at, updated_at)
                VALUES (?, 15, TIMESTAMPTZ '2030-01-01T00:00:00Z', TIMESTAMPTZ '2030-01-08T00:00:00Z', now(), now())
                """, userId);
        Long surveyId = jdbcTemplate.queryForObject("""
                INSERT INTO survey (user_id, title, external_url, target_count, duration_minutes, pay_minutes,
                                    status, created_at, updated_at)
                VALUES (?, '등록 차감 테스트 설문', 'https://example.com/form', 5, 5, 5, 'OPEN', now(), now())
                RETURNING id
                """, Long.class, userId);
        insertHistory(userId, "SIGNUP_BONUS_GRANTED", null, 0, 15, 15);
        insertHistory(otherId, "SURVEY_RESPONSE", null, 80, 0, 80);
        insertHistory(userId, "SURVEY_REGISTERED", surveyId, 3, -28, -10);

        mvc.perform(helpTime(userId).param("userId", otherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpTimeMinutes").value(-10))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].reason").value("SURVEY_REGISTERED"))
                .andExpect(jsonPath("$.data.items[0].surveyTitle").value("등록 차감 테스트 설문"))
                .andExpect(jsonPath("$.data.items[0].createdAt").isString())
                .andExpect(jsonPath("$.data.items[0].changeMinutes").value(-25))
                .andExpect(jsonPath("$.data.items[0].balanceAfter").value(-10))
                .andExpect(jsonPath("$.data.items[0].expiresAt").isEmpty())
                .andExpect(jsonPath("$.data.items[1].reason").value("SIGNUP_BONUS_GRANTED"))
                .andExpect(jsonPath("$.data.items[1].surveyTitle").isEmpty())
                .andExpect(jsonPath("$.data.items[1].expiresAt").value("2030-01-08T00:00:00Z"));

        mvc.perform(helpTime(userId).param("filter", "EARNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpTimeMinutes").value(-10))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].changeMinutes").value(15))
                .andExpect(jsonPath("$.data.items[0].balanceAfter").value(15));

        mvc.perform(helpTime(userId).param("filter", "SPENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reason").value("SURVEY_REGISTERED"))
                .andExpect(jsonPath("$.data.items[0].surveyTitle").value("등록 차감 테스트 설문"))
                .andExpect(jsonPath("$.data.items[0].changeMinutes").value(-25));
    }

    private MockHttpServletRequestBuilder helpTime(UUID userId) {
        return get(HELP_TIME).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issueAccessToken(userId).value());
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

    private void insertHistory(UUID userId, String reason, Long surveyId, int amount, int bonusAmount, int balanceAfter) {
        jdbcTemplate.update("""
                WITH transfer AS (
                    INSERT INTO time_transfer (type, survey_id, created_at, updated_at)
                    VALUES (?, ?, now(), now()) RETURNING id
                )
                INSERT INTO time_transaction (time_account_id, time_transfer_id, amount, bonus_amount,
                                              balance_after, created_at, updated_at)
                SELECT time_account.id, transfer.id, ?, ?, ?, now(), now()
                FROM time_account CROSS JOIN transfer
                WHERE time_account.user_id = ?
                """, reason, surveyId, amount, bonusAmount, balanceAfter, userId);
    }
}
