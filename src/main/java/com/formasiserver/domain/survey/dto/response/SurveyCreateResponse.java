package com.formasiserver.domain.survey.dto.response;

import com.formasiserver.domain.survey.entity.SurveyStatus;

public record SurveyCreateResponse(long surveyId, SurveyStatus status, int chargedMinutes, long balanceAfter) {
}
