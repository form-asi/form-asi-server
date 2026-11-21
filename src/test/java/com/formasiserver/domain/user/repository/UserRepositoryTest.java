package com.formasiserver.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserProfile;
import com.formasiserver.domain.user.entity.UserProfileField;
import com.formasiserver.domain.user.entity.UserProfilePet;
import com.formasiserver.domain.user.entity.UserRole;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.entity.profile.ChildrenCount;
import com.formasiserver.domain.user.entity.profile.Field;
import com.formasiserver.domain.user.entity.profile.Gender;
import com.formasiserver.domain.user.entity.profile.PetType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class UserRepositoryTest {

    private final String kakaoId = Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void deleteTestUser() {
        userRepository.findByKakaoId(kakaoId).ifPresent(userRepository::delete);
    }

    @Test
    void persistsNewMemberDefaultsAndAuditInstants() {
        Instant beforeInsert = Instant.now();
        User user = userRepository.saveAndFlush(User.of(kakaoId));
        Instant afterInsert = Instant.now();
        User storedUser = userRepository.findByKakaoId(kakaoId).orElseThrow();

        assertThat(storedUser.getUserId()).isNotNull().isEqualTo(user.getUserId());
        assertThat(storedUser.getBirthDate()).isNull();
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

    @Test
    void checksAndEnforcesNicknameUniquenessUsingTheSameCaseRules() {
        User firstUser = userRepository.saveAndFlush(User.of(kakaoId));
        User secondUser = userRepository.saveAndFlush(User.of(UUID.randomUUID().toString()));
        String nickname = "한A" + UUID.randomUUID().toString().substring(0, 8);

        try {
            assertThat(userRepository.existsByNicknameIgnoringCase(nickname)).isFalse();
            jdbcTemplate.update("UPDATE users SET nickname = ? WHERE user_id = ?", nickname, firstUser.getUserId());
            String differentCase = nickname.replace("A", "a");

            assertThat(userRepository.existsByNicknameIgnoringCase(differentCase)).isTrue();
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE users SET nickname = ? WHERE user_id = ?", differentCase, secondUser.getUserId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_users_nickname_ignore_case");
        } finally {
            userRepository.delete(secondUser);
        }
    }

    @Test
    @Transactional
    void mapsOnboardingProfileAndKeepsUnansweredAndNegativeChoicesDistinct() {
        User user = userRepository.saveAndFlush(User.of(kakaoId));
        UUID userId = user.getUserId();
        jdbcTemplate.update("""
                INSERT INTO user_profile_fields (user_id, field, created_at, updated_at)
                VALUES (?, 'DESIGN', now(), now())
                """, userId);
        Long fieldId = jdbcTemplate.queryForObject("""
                INSERT INTO user_profile_fields (user_id, field, field_etc, created_at, updated_at)
                VALUES (?, 'OTHER', '환경 연구', now(), now())
                RETURNING id
                """, Long.class, userId);
        jdbcTemplate.update("""
                INSERT INTO user_profile_pets (user_id, pet_type, created_at, updated_at)
                VALUES (?, 'DOG', now(), now())
                """, userId);
        Long petId = jdbcTemplate.queryForObject("""
                INSERT INTO user_profile_pets (user_id, pet_type, created_at, updated_at)
                VALUES (?, 'OTHER', now(), now())
                RETURNING id
                """, Long.class, userId);

        assertThat(jdbcTemplate.update("""
                INSERT INTO user_profile_fields (user_id, field, field_etc, created_at, updated_at)
                VALUES (?, 'OTHER', '다른 분야', now(), now())
                ON CONFLICT ON CONSTRAINT uk_user_profile_fields_user_code DO NOTHING
                """, userId)).isZero();
        assertThat(jdbcTemplate.update("""
                INSERT INTO user_profile_pets (user_id, pet_type, created_at, updated_at)
                VALUES (?, 'DOG', now(), now())
                ON CONFLICT ON CONSTRAINT uk_user_profile_pets_user_code DO NOTHING
                """, userId)).isZero();

        jdbcTemplate.update("""
                INSERT INTO user_profile (user_id, gender, job, job_etc, marital_status, marital_status_etc,
                                          children, pet_experience, created_at, updated_at)
                VALUES (?, 'FEMALE', 'OTHER', '연구 지원', 'OTHER', '직접 입력', 'NONE', 'PAST', now(), now())
                """, userId);
        UserProfile profile = entityManager.find(UserProfile.class, userId);
        assertThat(profile.getUser()).isSameAs(user);
        assertThat(profile.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(profile.getOccupationOther()).isEqualTo("연구 지원");
        assertThat(profile.getMaritalStatusOther()).isEqualTo("직접 입력");
        assertThat(profile.getChildrenCount()).isEqualTo(ChildrenCount.NONE);
        assertThat(profile.getHouseholdSize()).isNull();

        UserProfileField customField = entityManager.find(UserProfileField.class, fieldId);
        assertThat(customField.getCode()).isEqualTo(Field.OTHER);
        assertThat(customField.getCustomText()).isEqualTo("환경 연구");
        assertThat(customField.getUser()).isSameAs(user);
        assertThat(customField.getCreatedAt()).isNotNull();
        UserProfilePet pet = entityManager.find(UserProfilePet.class, petId);
        assertThat(pet.getCode()).isEqualTo(PetType.OTHER);
        assertThat(pet.getUser()).isSameAs(user);
        assertThat(pet.getCreatedAt()).isNotNull();
    }

    private boolean insertAtBarrier(CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            userRepository.saveAndFlush(User.of(kakaoId));
            return true;
        } catch (DataIntegrityViolationException exception) {
            assertThat(exception).hasMessageContaining("uk_users_kakao_id");
            return false;
        }
    }

}
