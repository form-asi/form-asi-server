package com.formasiserver.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserRole;
import com.formasiserver.domain.user.entity.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class UserRepositoryTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private final String kakaoId = Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void deleteTestUser() {
        userRepository.findByKakaoId(kakaoId).ifPresent(userRepository::delete);
    }

    @Test
    void persistsNewMemberDefaultsAndAuditInstants() {
        Instant beforeInsert = Instant.now();
        User user = userRepository.saveAndFlush(User.of(kakaoId, BIRTH_DATE));
        Instant afterInsert = Instant.now();
        User storedUser = userRepository.findByKakaoId(kakaoId).orElseThrow();

        assertThat(storedUser.getUserId()).isNotNull().isEqualTo(user.getUserId());
        assertThat(storedUser.getBirthDate()).isEqualTo(BIRTH_DATE);
        assertThat(storedUser.getNickname()).isNull();
        assertThat(storedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(storedUser.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(storedUser.isNotificationEnabled()).isTrue();
        // PostgreSQL stores timestamps with microsecond precision.
        assertThat(storedUser.getCreatedAt())
                .isBetween(beforeInsert.truncatedTo(ChronoUnit.MICROS), afterInsert.plus(1, ChronoUnit.MICROS));
        assertThat(storedUser.getUpdatedAt()).isEqualTo(storedUser.getCreatedAt());

        assertThat(jdbcTemplate.queryForMap("""
                SELECT pg_typeof(birth_date)::text AS birth_date,
                       pg_typeof(created_at)::text AS created_at,
                       pg_typeof(updated_at)::text AS updated_at
                FROM users
                WHERE user_id = ?
                """, user.getUserId()))
                .containsEntry("birth_date", "date")
                .containsEntry("created_at", "timestamp with time zone")
                .containsEntry("updated_at", "timestamp with time zone");
    }

    @Test
    void allowsOnlyOneOfTwoConcurrentInsertsForSameKakaoId() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> insertAtBarrier(start));
            var second = executor.submit(() -> insertAtBarrier(start));

            assertThat(new boolean[] {first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(userRepository.findByKakaoId(kakaoId)).isPresent();
    }

    private boolean insertAtBarrier(CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            userRepository.saveAndFlush(User.of(kakaoId, BIRTH_DATE));
            return true;
        } catch (DataIntegrityViolationException exception) {
            assertThat(exception).hasMessageContaining("uk_users_kakao_id");
            return false;
        }
    }

}
