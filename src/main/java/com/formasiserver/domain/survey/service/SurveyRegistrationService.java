package com.formasiserver.domain.survey.service;

import com.formasiserver.domain.helptime.service.HelpTimeService;
import com.formasiserver.domain.survey.dto.request.SurveyCreateRequest;
import com.formasiserver.domain.survey.dto.response.SurveyCreateResponse;
import com.formasiserver.domain.survey.entity.Survey;
import com.formasiserver.domain.survey.entity.SurveyPurpose;
import com.formasiserver.domain.survey.entity.SurveyTargetCondition;
import com.formasiserver.domain.survey.repository.SurveyPurposeRepository;
import com.formasiserver.domain.survey.repository.SurveyRepository;
import com.formasiserver.domain.survey.repository.SurveyTargetConditionRepository;
import com.formasiserver.domain.survey.utils.SurveyCreateRequestValidator;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurveyRegistrationService {

    private final UserRepository userRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyPurposeRepository surveyPurposeRepository;
    private final SurveyTargetConditionRepository surveyTargetConditionRepository;
    private final HelpTimeService helpTimeService;

    @Transactional
    public SurveyCreateResponse register(UUID userId, SurveyCreateRequest request) {
        User user = requireActiveUser(userId);
        SurveyCreateRequestValidator.validate(request);

        Survey survey = surveyRepository.save(Survey.builder()
                .user(user)
                .title(request.title())
                .externalUrl(request.externalUrl())
                .targetCount(request.targetCount())
                .durationMinutes(request.durationMinutes())
                .targetDescription(request.targetDescription())
                .rewardDescription(request.rewardDescription())
                .introduction(request.introduction())
                .minAge(request.minAge())
                .maxAge(request.maxAge())
                .build());
        surveyPurposeRepository.save(SurveyPurpose.of(survey, request.purpose(), request.purposeOther(),
                request.method(), request.methodOther()));
        if (request.targetConditions() != null) {
            surveyTargetConditionRepository.saveAll(request.targetConditions().entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(value -> SurveyTargetCondition.of(survey, entry.getKey(), value)))
                    .toList());
        }

        int chargedMinutes = Math.multiplyExact(survey.getDurationMinutes(), survey.getTargetCount());
        long balanceAfter = helpTimeService.reserveForSurveyRegistration(survey);
        return new SurveyCreateResponse(survey.getId(), survey.getStatus(), chargedMinutes, balanceAfter);
    }

    private User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return switch (user.getStatus()) {
            case PENDING -> throw new ApiException(ErrorCode.PROFILE_REQUIRED);
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case ACTIVE -> user;
        };
    }
}
