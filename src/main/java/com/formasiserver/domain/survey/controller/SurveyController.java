package com.formasiserver.domain.survey.controller;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.survey.dto.request.SurveyCreateRequest;
import com.formasiserver.domain.survey.dto.response.SurveyCreateResponse;
import com.formasiserver.domain.survey.service.SurveyRegistrationService;
import com.formasiserver.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/surveys")
public class SurveyController implements SurveyApi {

    private final SurveyRegistrationService surveyRegistrationService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BaseResponse<SurveyCreateResponse> register(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SurveyCreateRequest request) {
        return BaseResponse.success(surveyRegistrationService.register(principal.userId(), request));
    }
}
