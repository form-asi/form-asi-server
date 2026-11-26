package com.formasiserver.domain.helptime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formasiserver.domain.helptime.dto.request.HelpTimeFilter;
import com.formasiserver.domain.survey.entity.Survey;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class HelpTimeReservationTest {

    @Autowired
    private HelpTimeService helpTimeService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @CsvSource({
            "10, 60, ACTIVE, 0, 50",
            "60, 20, ACTIVE, 30, 20",
            "-10, 60, ACTIVE, 0, 50",
            "55, 20, EXPIRED, 50, 0",
            "55, 20, RECLAIMED, 50, 0",
            "55, 20, FUTURE, 50, 0",
            "55, 20, MISSING, 50, 0",
            "2147483647, 100, ACTIVE, 0, 50"
    })
    void movesTheEligibleMinutesAndRecordsBothSides(int regularBalance, int bonusBalance, String bonusStatus,
            int regularReserved, int bonusReserved) {
        Survey survey = createSurvey();
        UUID userId = survey.getUser().getUserId();
        insertUserAccount(userId, regularBalance, bonusBalance);
        insertBonus(userId, bonusBalance, bonusStatus);

        long balanceAfter = (long) regularBalance + bonusBalance - 50;
        assertThat(helpTimeService.reserveForSurveyRegistration(survey)).isEqualTo(balanceAfter);
        entityManager.flush();

        assertThat(jdbcTemplate.queryForMap("""
                SELECT user_id, owner_type, balance, bonus_balance FROM time_account WHERE survey_id = ?
                """, survey.getId()))
                .containsEntry("user_id", null)
                .containsEntry("owner_type", "SURVEY")
                .containsEntry("balance", regularReserved)
                .containsEntry("bonus_balance", bonusReserved);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT balance, bonus_balance FROM time_account WHERE user_id = ?
                """, userId))
                .containsEntry("balance", regularBalance - regularReserved)
                .containsEntry("bonus_balance", bonusBalance - bonusReserved);

        List<LedgerEntry> entries = jdbcTemplate.query("""
                SELECT a.owner_type, t.amount, t.bonus_amount, t.balance_after
                FROM time_transaction t
                JOIN time_account a ON a.id = t.time_account_id
                JOIN time_transfer f ON f.id = t.time_transfer_id
                WHERE f.survey_id = ? AND f.type = 'SURVEY_REGISTERED'
                """, (rs, row) -> new LedgerEntry(rs.getString("owner_type"), rs.getInt("amount"),
                rs.getInt("bonus_amount"), rs.getLong("balance_after")), survey.getId());
        assertThat(entries).containsExactlyInAnyOrder(
                new LedgerEntry("USER", -regularReserved, -bonusReserved, balanceAfter),
                new LedgerEntry("SURVEY", regularReserved, bonusReserved, 50));

        assertThat(helpTimeService.getHistory(userId, HelpTimeFilter.SPENT).items()).singleElement().satisfies(item -> {
            assertThat(item.reason()).isEqualTo("SURVEY_REGISTERED");
            assertThat(item.changeMinutes()).isEqualTo(-50);
            assertThat(item.balanceAfter()).isEqualTo(balanceAfter);
        });
    }

    @Test
    void rejectsReservationWhenOnlyExpiredMinutesCoverTheCost() {
        Survey survey = createSurvey();
        UUID userId = survey.getUser().getUserId();
        insertUserAccount(userId, 40, 20);
        insertBonus(userId, 20, "EXPIRED");

        assertThatThrownBy(() -> helpTimeService.reserveForSurveyRegistration(survey))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_HELP_TIME);
                    assertThat(exception).hasMessage("10분이 모자라요. 응답 수나 소요 시간을 줄여주세요.");
                });
        assertThat(jdbcTemplate.queryForMap("""
                SELECT balance, bonus_balance FROM time_account WHERE user_id = ?
                """, userId)).containsEntry("balance", 40).containsEntry("bonus_balance", 20);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM time_account WHERE survey_id = ?",
                Long.class, survey.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM time_transfer WHERE survey_id = ?",
                Long.class, survey.getId())).isZero();
    }

    @Test
    void allowsOnlyOneReserveAccountPerSurvey() {
        Survey survey = createSurvey();
        insertUserAccount(survey.getUser().getUserId(), 50, 0);
        assertThat(helpTimeService.reserveForSurveyRegistration(survey)).isZero();

        int inserted = jdbcTemplate.update("""
                INSERT INTO time_account (survey_id, owner_type, created_at, updated_at)
                VALUES (?, 'SURVEY', now(), now())
                ON CONFLICT ON CONSTRAINT uk_time_account_survey_id DO NOTHING
                """, survey.getId());

        assertThat(inserted).isZero();
    }

    private Survey createSurvey() {
        User user = User.of(UUID.randomUUID().toString());
        entityManager.persist(user);
        Survey survey = Survey.builder()
                .user(user)
                .title("도움 시간 보관 테스트")
                .externalUrl("https://example.com/form")
                .durationMinutes(10)
                .targetCount(5)
                .build();
        entityManager.persist(survey);
        return survey;
    }

    private void insertUserAccount(UUID userId, int regularBalance, int bonusBalance) {
        jdbcTemplate.update("""
                INSERT INTO time_account (user_id, balance, bonus_balance, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, userId, regularBalance, bonusBalance);
    }

    private void insertBonus(UUID userId, int bonusBalance, String status) {
        if (status.equals("MISSING")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO signup_bonus (user_id, granted_minutes, granted_at, expires_at, reclaimed_at,
                                          created_at, updated_at)
                VALUES (?, ?,
                        CASE WHEN ? = 'FUTURE' THEN now() + interval '1 day' ELSE now() - interval '2 days' END,
                        CASE WHEN ? = 'EXPIRED' THEN now() - interval '1 day' ELSE now() + interval '2 days' END,
                        CASE WHEN ? = 'RECLAIMED' THEN now() - interval '1 hour' END,
                        now(), now())
                """, userId, bonusBalance, status, status, status);
    }

    private record LedgerEntry(String ownerType, int amount, int bonusAmount, long balanceAfter) {
    }
}
