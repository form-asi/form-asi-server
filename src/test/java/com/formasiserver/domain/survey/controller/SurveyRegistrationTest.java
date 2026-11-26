package com.formasiserver.domain.survey.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SurveyRegistrationTest {

    private static final String SURVEYS = "/api/v1/surveys";
    private static final String BODY = """
            {"externalUrl":"https://example.com/form","purpose":"OTHER","purposeOther":"업무 개선",
             "method":"OTHER","methodOther":"현장 관찰","title":"업무 도구 조사","targetCount":5,"durationMinutes":10,
             "targetConditions":{"GENDER":["FEMALE"],"OCCUPATION":["EMPLOYEE","FREELANCER","EMPLOYEE"]}}
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AuthTokenRedisRepository tokenRepository;

    private final List<UUID> createdUsers = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        for (UUID userId : createdUsers) {
            jdbcTemplate.update("""
                    DELETE FROM time_transaction WHERE time_transfer_id IN (
                        SELECT t.id FROM time_transfer t JOIN survey s ON s.id = t.survey_id WHERE s.user_id = ?
                    )
                    """, userId);
            for (String table : List.of("time_transfer", "time_account", "survey_target_condition", "survey_purpose")) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE survey_id IN (SELECT id FROM survey WHERE user_id = ?)",
                        userId);
            }
            jdbcTemplate.update("DELETE FROM survey WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM time_account WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    @Test
    void publishesTheSurveyAndPersistsItsClassificationAndConditions() throws Exception {
        UUID userId = member("ACTIVE", 50);
        MockHttpServletResponse response = mvc.perform(register(access(userId), BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.chargedMinutes").value(50))
                .andExpect(jsonPath("$.data.balanceAfter").value(0))
                .andReturn().getResponse();
        long surveyId = surveyId(response);

        assertThat(jdbcTemplate.queryForMap("SELECT user_id, status FROM survey WHERE id = ?", surveyId))
                .containsEntry("user_id", userId).containsEntry("status", "OPEN");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT purpose, purpose_other, method, method_other FROM survey_purpose WHERE survey_id = ?
                """, surveyId)).containsEntry("purpose", "OTHER").containsEntry("purpose_other", "업무 개선")
                .containsEntry("method", "OTHER").containsEntry("method_other", "현장 관찰");
        assertThat(jdbcTemplate.queryForList("""
                SELECT item || ':' || value FROM survey_target_condition WHERE survey_id = ?
                """, String.class, surveyId)).containsExactlyInAnyOrder(
                "GENDER:FEMALE", "OCCUPATION:EMPLOYEE", "OCCUPATION:FREELANCER");
        assertPublished(userId, 1, 0);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NONE | 50분이 모자라요. 응답 수나 소요 시간을 줄여주세요.",
            "-10 | 도움 시간이 마이너스인 동안은 설문을 올릴 수 없어요.",
            "40 | 10분이 모자라요. 응답 수나 소요 시간을 줄여주세요."
    }, delimiter = '|', nullValues = "NONE")
    void rejectsMissingOrInsufficientBalanceWithoutPublishing(Integer balance, String message) throws Exception {
        UUID userId = member("ACTIVE", balance);
        mvc.perform(register(access(userId), BODY)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_HELP_TIME"))
                .andExpect(jsonPath("$.error.message").value(message));
        assertPublished(userId, 0, balance);
    }

    @Test
    void requiresAnActiveMemberAndValidInput() throws Exception {
        mvc.perform(post(SURVEYS).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        UUID pending = member("PENDING", 60);
        mvc.perform(register(access(pending), BODY)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROFILE_REQUIRED"));

        UUID active = member("ACTIVE", 60);
        mvc.perform(register(access(active), BODY.replace("https://", "http://")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value("externalUrl"))
                .andExpect(jsonPath("$.error.errors[0].message").value("https://로 시작하는 설문 링크를 입력해주세요."));
        assertPublished(pending, 0, 60);
        assertPublished(active, 0, 60);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "13 | 30 | minAge | 만 14세 이상만 이용할 수 있어요.",
            "14 | 101 | maxAge | 100세 이하로 설정해주세요.",
            "35 | 30 | minAge | 최소 나이는 최대 나이보다 클 수 없어요."
    }, delimiter = '|')
    void explainsInvalidTargetAges(int minAge, int maxAge, String field, String message) throws Exception {
        UUID userId = member("ACTIVE", 60);
        var body = objectMapper.readTree(BODY).asObject();
        body.put("minAge", minAge).put("maxAge", maxAge);

        mvc.perform(register(access(userId), objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value(field))
                .andExpect(jsonPath("$.error.errors[0].message").value(message));
    }

    @Test
    void documentsTheRegistrationEndpoint() throws Exception {
        String operation = "$.paths['/api/v1/surveys'].post";
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".requestBody").exists())
                .andExpect(jsonPath(operation + ".responses['201']").exists())
                .andExpect(jsonPath(operation + ".responses['409']").exists());
    }

    @Test
    void chargesOnlyOneOfTwoConcurrentRegistrationsWhenBalanceCoversOne() throws Exception {
        UUID userId = member("ACTIVE", 60);
        String access = access(userId);
        CyclicBarrier start = new CyclicBarrier(2);
        List<MockHttpServletResponse> responses;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registerAtBarrier(start, access, BODY));
            var second = executor.submit(() -> registerAtBarrier(start, access, BODY.replace("업무 도구 조사", "다른 설문")));
            responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        MockHttpServletResponse rejected = responses.stream().filter(response -> response.getStatus() == 409)
                .findFirst().orElseThrow();
        assertThat(objectMapper.readTree(rejected.getContentAsByteArray()).path("error").path("code").asString())
                .isEqualTo("INSUFFICIENT_HELP_TIME");
        assertThat(objectMapper.readTree(rejected.getContentAsByteArray()).path("error").path("message").asString())
                .isEqualTo("40분이 모자라요. 응답 수나 소요 시간을 줄여주세요.");
        assertPublished(userId, 1, 10);
    }

    private MockHttpServletResponse registerAtBarrier(CyclicBarrier start, String access, String body) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(register(access, body)).andReturn().getResponse();
    }

    private void assertPublished(UUID userId, int published, Integer remaining) {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM survey WHERE user_id = ?",
                Long.class, userId)).isEqualTo(published);
        for (String table : List.of("time_account", "time_transfer")) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE survey_id IN (SELECT id FROM survey WHERE user_id = ?)",
                    Long.class, userId)).isEqualTo(published);
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM time_transaction t JOIN time_transfer f ON f.id = t.time_transfer_id
                JOIN survey s ON s.id = f.survey_id WHERE s.user_id = ?
                """, Long.class, userId)).isEqualTo(2L * published);
        assertThat(jdbcTemplate.queryForList("SELECT balance FROM time_account WHERE user_id = ?", Integer.class, userId))
                .containsExactlyElementsOf(remaining == null ? List.of() : List.of(remaining));
    }

    private UUID member(String status, Integer balance) {
        UUID userId = UUID.randomUUID();
        createdUsers.add(userId);
        jdbcTemplate.update("""
                INSERT INTO users (user_id, kakao_id, birth_date, role, status, created_at, updated_at)
                VALUES (?, ?, DATE '2000-01-01', 'USER', ?, now(), now())
                """, userId, userId.toString(), status);
        if (balance != null) {
            jdbcTemplate.update("""
                    INSERT INTO time_account (user_id, balance, created_at, updated_at) VALUES (?, ?, now(), now())
                    """, userId, balance);
        }
        return userId;
    }

    private String access(UUID userId) {
        return "Bearer " + jwtProvider.issueAccessToken(userId).value();
    }

    private static MockHttpServletRequestBuilder register(String access, String body) {
        return post(SURVEYS).header(HttpHeaders.AUTHORIZATION, access)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long surveyId(MockHttpServletResponse response) {
        return objectMapper.readTree(response.getContentAsByteArray()).path("data").path("surveyId").asLong();
    }
}
