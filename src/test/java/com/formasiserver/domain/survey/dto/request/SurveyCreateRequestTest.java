package com.formasiserver.domain.survey.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formasiserver.domain.survey.entity.SurveyMethod;
import com.formasiserver.domain.survey.entity.SurveyPurposeCode;
import com.formasiserver.domain.survey.utils.SurveyCreateRequestValidator;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import com.formasiserver.global.response.ErrorResponse.ValidationError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

class SurveyCreateRequestTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATORS.close();
    }

    @Test
    void acceptsMissingOptionalConditionsAndOneSidedAgeRanges() {
        assertThat(invalidFields(request(5, 10, null, null, null))).isEmpty();
        assertThat(invalidFields(request(5, 10, 14, null, Map.of()))).isEmpty();
        assertThat(invalidFields(request(5, 10, null, 100,
                Map.of("GENDER", Set.of("MALE", "FEMALE"))))).isEmpty();
    }

    @Test
    void rejectsRequiredFieldsAndUnsupportedChoices() {
        SurveyCreateRequest missing = new SurveyCreateRequest(" ", null, null, null, null,
                " ", 0, null, null, null, null, null, null, null);
        SurveyCreateRequest unsupported = new SurveyCreateRequest("http://example.com/form",
                SurveyPurposeCode.ACADEMIC_RESEARCH, null, SurveyMethod.MULTIPLE_CHOICE, null,
                "설문 제목", 5, 4, null, null, null, null, null, null);

        assertThat(invalidFields(missing)).containsExactlyInAnyOrder(
                "externalUrl", "purpose", "method", "title", "targetCount", "durationMinutes");
        assertThat(invalidFields(unsupported)).containsExactlyInAnyOrder("externalUrl", "durationMinutes");
        assertThat(invalidFields(request(5, 10, null, 101, null))).containsExactly("maxAge");
        assertThat(invalidFields(request(5, 10, 30, 25,
                Map.of("GENDER", Set.of("EMPLOYEE")))))
                .containsExactlyInAnyOrder("minAge", "targetConditions");
        assertThat(invalidFields(request(5, 10, null, null, Map.of("UNKNOWN", Set.of("MALE")))))
                .containsExactly("targetConditions");
    }

    @Test
    void treatsRepeatedTargetValuesAsOneChoice() {
        SurveyCreateRequest request = JsonMapper.builder().build().readValue("""
                {"externalUrl":"https://example.com/form","purpose":"SCHOOL_ASSIGNMENT","method":"PARTICIPATORY",
                 "title":"설문 제목","targetCount":5,"durationMinutes":10,
                 "targetConditions":{"PET_TYPE":["DOG","DOG","CAT"]}}
                """, SurveyCreateRequest.class);

        assertThat(request.targetConditions().get("PET_TYPE")).containsExactlyInAnyOrder("DOG", "CAT");
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void rejectsCostBeyondTheLedgerIntegerRangeWithoutOverflow() {
        int maxTargetCount = Integer.MAX_VALUE / 60;

        assertThat(invalidFields(request(maxTargetCount, 60, null, null, null))).isEmpty();
        assertThat(invalidFields(request(maxTargetCount + 1, 60, null, null, null))).containsExactly("targetCount");
    }

    @Test
    void requiresTextForEachOtherChoiceIndependently() {
        assertThat(invalidFields(classificationRequest(SurveyPurposeCode.OTHER, null, SurveyMethod.OTHER, "\t ")))
                .containsExactlyInAnyOrder("purposeOther", "methodOther");
        assertThat(invalidFields(classificationRequest(SurveyPurposeCode.OTHER, "서비스 운영 의견 수집",
                SurveyMethod.MULTIPLE_CHOICE, null))).isEmpty();
        assertThat(invalidFields(classificationRequest(SurveyPurposeCode.SCHOOL_ASSIGNMENT, null,
                SurveyMethod.OTHER, "현장 관찰 후 응답"))).isEmpty();
        assertThat(invalidFields(classificationRequest(SurveyPurposeCode.OTHER, "가".repeat(100),
                SurveyMethod.OTHER, "나".repeat(100)))).isEmpty();
    }

    @Test
    void rejectsUnexpectedOrOverlongOtherText() {
        assertThat(invalidFields(classificationRequest(SurveyPurposeCode.SCHOOL_ASSIGNMENT, "기타 목적",
                SurveyMethod.MULTIPLE_CHOICE, "기타 방식"))).containsExactlyInAnyOrder("purposeOther", "methodOther");
        assertThat(invalidFields(classificationRequest(SurveyPurposeCode.OTHER, "가".repeat(101),
                SurveyMethod.OTHER, "나".repeat(101)))).containsExactlyInAnyOrder("purposeOther", "methodOther");
    }

    @Test
    void mapsNamedPurposeAndMethodCodesAndRejectsArraysAndUnknownNames() {
        JsonMapper mapper = JsonMapper.builder().build();

        SurveyCreateRequest parsed = mapper.readValue("""
                {"purpose":"OTHER","purposeOther":"서비스 개선","method":"OTHER","methodOther":"현장 관찰",
                 "targetCount":5,"minAge":null}
                """, SurveyCreateRequest.class);
        assertThat(parsed.purpose()).isEqualTo(SurveyPurposeCode.OTHER);
        assertThat(parsed.purposeOther()).isEqualTo("서비스 개선");
        assertThat(parsed.method()).isEqualTo(SurveyMethod.OTHER);
        assertThat(parsed.methodOther()).isEqualTo("현장 관찰");
        assertThat(parsed.targetCount()).isEqualTo(5);
        assertThat(parsed.minAge()).isNull();

        for (String json : List.of("{\"purpose\":[\"SCHOOL_ASSIGNMENT\"]}", "{\"method\":\"UNKNOWN\"}")) {
            assertThatThrownBy(() -> mapper.readValue(json, SurveyCreateRequest.class))
                    .as(json)
                    .isInstanceOf(MismatchedInputException.class);
        }
    }

    private static SurveyCreateRequest request(int targetCount, int duration, Integer minAge, Integer maxAge,
            Map<String, Set<String>> targetConditions) {
        return new SurveyCreateRequest("https://example.com/form?q=1", SurveyPurposeCode.ACADEMIC_RESEARCH, null,
                SurveyMethod.MULTIPLE_CHOICE, null, "설문 제목", targetCount, duration,
                minAge, maxAge, targetConditions, null, null, null);
    }

    private static SurveyCreateRequest classificationRequest(SurveyPurposeCode purpose, String purposeOther,
            SurveyMethod method, String methodOther) {
        return new SurveyCreateRequest("https://example.com/form", purpose, purposeOther, method, methodOther,
                "설문 제목", 5, 10, null, null, null, null, null, null);
    }

    private static List<String> invalidFields(SurveyCreateRequest request) {
        List<String> fieldErrors = VALIDATORS.getValidator().validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Path::toString)
                .toList();
        if (!fieldErrors.isEmpty()) {
            return fieldErrors;
        }
        try {
            SurveyCreateRequestValidator.validate(request);
            return List.of();
        } catch (ApiException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            return exception.getValidationErrors().stream().map(ValidationError::field).toList();
        }
    }
}
