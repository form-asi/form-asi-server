package com.formasiserver.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserRole;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingTest {

    private static final String ONBOARDING = "/api/v1/users/me/onboarding";
    private static final String ME = "/api/v1/users/me";

    private final List<UUID> createdUsers = new ArrayList<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeTestUsers() {
        for (UUID userId : createdUsers) {
            jdbcTemplate.update("DELETE FROM user_profile_pets WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_profile_fields WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
    }

    @Test
    void storesTheWholeProfileAndReadsActiveWithTheSameAccessToken() throws Exception {
        UUID userId = newMember();
        UUID otherId = newMember();
        String access = access(userId);
        String nickname = nickname();
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.age").isEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        mvc.perform(onboarding(access, """
                {
                  "nickname": "%s", "birthDate": "2000-01-01", "gender": "MALE",
                  "occupation": "OTHER", "occupationOther": "현장 연구",
                  "fields": ["DESIGN", "OTHER"], "fieldOther": "환경 연구", "region": "SEOUL",
                  "householdComposition": "OTHER", "householdCompositionOther": "동료와 거주",
                  "householdSize": "TWO", "maritalStatus": "OTHER", "maritalStatusOther": "직접 작성",
                  "childrenCount": "NONE", "petExperience": "PAST", "pets": ["DOG", "CAT"], "driverLicense": "CLASS_1"
                }
                """.formatted(nickname)).header("X-User-Id", otherId).param("userId", otherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.age").isNumber())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(userRepository.findById(userId).orElseThrow().getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM user_profile WHERE user_id = ?", userId))
                .containsEntry("job_etc", "현장 연구")
                .containsEntry("household_etc", "동료와 거주")
                .containsEntry("marital_status_etc", "직접 작성")
                .containsEntry("children", "NONE");
        assertThat(jdbcTemplate.queryForList(
                "SELECT field FROM user_profile_fields WHERE user_id = ?", String.class, userId))
                .containsExactlyInAnyOrder("DESIGN", "OTHER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT field_etc FROM user_profile_fields WHERE user_id = ? AND field = 'OTHER'",
                String.class, userId)).isEqualTo("환경 연구");
        assertThat(jdbcTemplate.queryForList(
                "SELECT pet_type FROM user_profile_pets WHERE user_id = ?", String.class, userId))
                .containsExactlyInAnyOrder("DOG", "CAT");
        assertPendingWithoutProfile(otherId);

        mvc.perform(onboarding(access, requiredProfile(nickname())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_ALREADY_COMPLETED"));
        assertThat(userRepository.findById(userId).orElseThrow().getNickname()).isEqualTo(nickname);
        assertThat(countRows("user_profile_fields", userId)).isEqualTo(2);
    }

    @Test
    void validatesBirthDateBeforeSavingTheProfile() throws Exception {
        UUID userId = newMember();
        String access = access(userId);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate adultDate = today.minusYears(14);
        LocalDate underageDate = adultDate.plusDays(1);
        String profile = requiredProfile(nickname());
        mvc.perform(onboarding(access, profile.replace("2000-01-01", underageDate.toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNDER_MINIMUM_AGE"));
        mvc.perform(onboarding(access, profile.replace("2000-01-01", today.plusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BIRTH_DATE_UNAVAILABLE"));
        mvc.perform(onboarding(access, profile.replace("\"birthDate\": \"2000-01-01\", ", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BIRTH_DATE_UNAVAILABLE"));
        assertPendingWithoutProfile(userId);
        mvc.perform(onboarding(access, profile.replace("2000-01-01", adultDate.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(userRepository.findById(userId).orElseThrow().getBirthDate()).isEqualTo(adultDate);
    }

    @Test
    void rejectsARequestWithoutRequiredFields() throws Exception {
        UUID userId = newMember();
        mvc.perform(onboarding(access(userId), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.errors[*].field").isArray())
                .andExpect(jsonPath("$.error.errors.length()").value(4));
        assertPendingWithoutProfile(userId);
    }

    @Test
    void rejectsAnUnknownChoiceCode() throws Exception {
        UUID userId = newMember();
        mvc.perform(onboarding(access(userId), requiredProfile(nickname()).replace("\"MALE\"", "\"UNKNOWN\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.errors[0].field").value("gender"));
        assertPendingWithoutProfile(userId);
    }

    @Test
    void validatesOtherTextDuplicateFieldsAndPetExperience() throws Exception {
        UUID userId = newMember();
        mvc.perform(onboarding(access(userId), """
                {
                  "nickname": "%s", "birthDate": "2000-01-01", "gender": "FEMALE", "occupation": "OTHER",
                  "fields": ["OTHER", "OTHER"], "fieldOther": " ",
                  "maritalStatus": "OTHER", "maritalStatusOther": "123456789012345678901",
                  "householdCompositionOther": "가구 구성 선택 없음", "petExperience": "NEVER", "pets": ["DOG"]
                }
                """.formatted(nickname())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.errors.length()").value(6));
        assertPendingWithoutProfile(userId);
    }

    @Test
    void preservesAnExistingBirthDateAndStoresOnlyForTheAuthenticatedMember() throws Exception {
        UUID userId = newMember();
        UUID otherId = newMember();
        jdbcTemplate.update("UPDATE users SET birth_date = ? WHERE user_id = ?", LocalDate.of(2000, 1, 1), userId);
        String body = """
                {
                  "nickname": "%s", "gender": "MALE", "occupation": "EMPLOYEE", "fields": ["LAW"],
                  "userId": "%s", "role": "ADMIN", "status": "SUSPENDED", "birthDate": "2001-01-01"
                }
                """.formatted(nickname(), otherId);
        mvc.perform(onboarding(access(userId), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        User storedUser = userRepository.findById(userId).orElseThrow();
        assertThat(storedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(storedUser.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertPendingWithoutProfile(otherId);
    }

    @Test
    void serializesTwoSubmissionsForTheSameMember() throws Exception {
        UUID userId = newMember();
        String access = access(userId);
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MockHttpServletResponse> first = executor.submit(() -> submitAtBarrier(start, access, nickname()));
            Future<MockHttpServletResponse> second = executor.submit(() -> submitAtBarrier(start, access, nickname()));
            MockHttpServletResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            MockHttpServletResponse secondResponse = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(firstResponse.getStatus(), secondResponse.getStatus())).containsExactlyInAnyOrder(200, 409);
            MockHttpServletResponse conflict = firstResponse.getStatus() == 409 ? firstResponse : secondResponse;
            assertThat(conflict.getContentAsString()).contains("ONBOARDING_ALREADY_COMPLETED");
        }
        assertThat(countRows("user_profile", userId)).isOne();
        assertThat(countRows("user_profile_fields", userId)).isOne();
        assertThat(countRows("user_profile_pets", userId)).isZero();
    }

    @Test
    void letsOnlyOneMemberCompleteWithTheSameNicknameIgnoringCase() throws Exception {
        UUID firstUser = newMember();
        UUID secondUser = newMember();
        String nickname = nickname();
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MockHttpServletResponse> first = executor.submit(() -> submitAtBarrier(start, access(firstUser), nickname));
            Future<MockHttpServletResponse> second = executor.submit(
                    () -> submitAtBarrier(start, access(secondUser), "a" + nickname.substring(1)));
            MockHttpServletResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            MockHttpServletResponse secondResponse = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(firstResponse.getStatus(), secondResponse.getStatus())).containsExactlyInAnyOrder(200, 409);
            UUID loser = firstResponse.getStatus() == 409 ? firstUser : secondUser;
            MockHttpServletResponse conflict = firstResponse.getStatus() == 409 ? firstResponse : secondResponse;
            assertThat(conflict.getContentAsString()).contains("NICKNAME_ALREADY_IN_USE");
            assertPendingWithoutProfile(loser);
        }
    }

    @Test
    void rollsBackTheMemberAndProfileWhenSavingAPetFails() throws Exception {
        UUID userId = newMember();
        jdbcTemplate.update("""
                INSERT INTO user_profile_pets (user_id, pet_type, created_at, updated_at)
                VALUES (?, 'DOG', now(), now())
                """, userId);
        String body = requiredProfile(nickname()).replace("}", ",\"petExperience\": \"CURRENT\", \"pets\": [\"DOG\"]}");
        mvc.perform(onboarding(access(userId), body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
        User storedUser = userRepository.findById(userId).orElseThrow();
        assertThat(storedUser.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(storedUser.getNickname()).isNull();
        assertThat(storedUser.getBirthDate()).isNull();
        assertThat(countRows("user_profile", userId)).isZero();
        assertThat(countRows("user_profile_fields", userId)).isZero();
        assertThat(countRows("user_profile_pets", userId)).isOne();
    }

    private void assertPendingWithoutProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getNickname()).isNull();
        assertThat(user.getBirthDate()).isNull();
        assertThat(countRows("user_profile", userId)).isZero();
        assertThat(countRows("user_profile_fields", userId)).isZero();
        assertThat(countRows("user_profile_pets", userId)).isZero();
    }

    private int countRows(String table, UUID userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private MockHttpServletResponse submitAtBarrier(CyclicBarrier start, String access, String nickname) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(onboarding(access, requiredProfile(nickname))).andReturn().getResponse();
    }

    private UUID newMember() {
        UUID userId = userRepository.saveAndFlush(User.of(UUID.randomUUID().toString())).getUserId();
        createdUsers.add(userId);
        return userId;
    }

    private String access(UUID userId) {
        return "Bearer " + jwtProvider.issueAccessToken(userId).value();
    }

    private String nickname() {
        return "A" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String requiredProfile(String nickname) {
        return """
                {"nickname": "%s", "birthDate": "2000-01-01", "gender": "MALE", "occupation": "EMPLOYEE", "fields": ["LAW"]}
                """.formatted(nickname);
    }

    private MockHttpServletRequestBuilder onboarding(String access, String body) {
        return post(ONBOARDING).header(HttpHeaders.AUTHORIZATION, access)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
