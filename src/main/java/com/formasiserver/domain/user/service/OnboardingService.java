package com.formasiserver.domain.user.service;

import com.formasiserver.domain.user.dto.request.OnboardingRequest;
import com.formasiserver.domain.user.dto.response.OnboardingResponse;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserProfile;
import com.formasiserver.domain.user.entity.UserProfileField;
import com.formasiserver.domain.user.entity.UserProfilePet;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.entity.profile.Field;
import com.formasiserver.domain.user.entity.profile.HouseholdComposition;
import com.formasiserver.domain.user.entity.profile.MaritalStatus;
import com.formasiserver.domain.user.entity.profile.Occupation;
import com.formasiserver.domain.user.entity.profile.PetExperience;
import com.formasiserver.domain.user.entity.profile.PetType;
import com.formasiserver.domain.user.repository.UserProfileFieldRepository;
import com.formasiserver.domain.user.repository.UserProfilePetRepository;
import com.formasiserver.domain.user.repository.UserProfileRepository;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import com.formasiserver.global.response.ErrorResponse.ValidationError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final int OTHER_TEXT_MAX_LENGTH = 20;
    private static final String PROFILE_PRIMARY_KEY = "user_profile_pkey";
    private static final String NICKNAME_UNIQUE_INDEX = "uk_users_nickname_ignore_case";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileFieldRepository userProfileFieldRepository;
    private final UserProfilePetRepository userProfilePetRepository;

    @Transactional
    public OnboardingResponse complete(UUID userId, OnboardingRequest request) {
        User user = requirePendingUser(userId);
        validateOptions(request);

        try {
            saveProfile(user, request);
            if (userRepository.activateOnboarding(userId, request.nickname(), Instant.now()) == 0) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation) {
                if (PROFILE_PRIMARY_KEY.equals(violation.getConstraintName())) {
                    throw new ApiException(ErrorCode.ONBOARDING_ALREADY_COMPLETED, exception);
                }
                if (NICKNAME_UNIQUE_INDEX.equals(violation.getConstraintName())) {
                    throw new ApiException(ErrorCode.NICKNAME_ALREADY_IN_USE, exception);
                }
            }
            throw exception;
        }
        return new OnboardingResponse(userId, request.nickname(), UserStatus.ACTIVE);
    }

    private User requirePendingUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return switch (user.getStatus()) {
            case ACTIVE -> throw new ApiException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case PENDING -> user;
        };
    }

    private void saveProfile(User user, OnboardingRequest request) {
        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(user)
                .gender(request.gender())
                .occupation(request.occupation())
                .occupationOther(request.occupationOther())
                .region(request.region())
                .householdComposition(request.householdComposition())
                .householdCompositionOther(request.householdCompositionOther())
                .householdSize(request.householdSize())
                .maritalStatus(request.maritalStatus())
                .maritalStatusOther(request.maritalStatusOther())
                .childrenCount(request.childrenCount())
                .petExperience(request.petExperience())
                .driverLicense(request.driverLicense())
                .build());
        userProfileFieldRepository.saveAll(request.fields().stream()
                .map(field -> UserProfileField.of(user, field, field == Field.OTHER ? request.fieldOther() : null))
                .toList());
        if (request.pets() != null && !request.pets().isEmpty()) {
            userProfilePetRepository.saveAll(request.pets().stream()
                    .map(pet -> UserProfilePet.of(user, pet))
                    .toList());
        }
    }

    private void validateOptions(OnboardingRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        validateOtherText(errors, "occupationOther", request.occupation() == Occupation.OTHER, request.occupationOther());
        validateOtherText(errors, "fieldOther", request.fields().contains(Field.OTHER), request.fieldOther());
        validateOtherText(errors, "householdCompositionOther",
                request.householdComposition() == HouseholdComposition.OTHER, request.householdCompositionOther());
        validateOtherText(errors, "maritalStatusOther",
                request.maritalStatus() == MaritalStatus.OTHER, request.maritalStatusOther());

        if (new HashSet<>(request.fields()).size() != request.fields().size()) {
            errors.add(new ValidationError("fields", "같은 분야를 여러 번 선택할 수 없습니다."));
        }

        List<PetType> pets = request.pets();
        if (pets != null && !pets.isEmpty()) {
            if (request.petExperience() != PetExperience.PAST && request.petExperience() != PetExperience.CURRENT) {
                errors.add(new ValidationError("pets", "반려동물을 키운 경험이 있을 때만 종류를 선택할 수 있습니다."));
            } else if (new HashSet<>(pets).size() != pets.size()) {
                errors.add(new ValidationError("pets", "같은 반려동물 종류를 여러 번 선택할 수 없습니다."));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validationFailed(errors);
        }
    }

    private void validateOtherText(List<ValidationError> errors, String field, boolean selected, @Nullable String text) {
        if (selected) {
            if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > OTHER_TEXT_MAX_LENGTH) {
                errors.add(new ValidationError(field, "기타 입력은 공백 포함 20자 이내로 입력해주세요."));
            }
        } else if (text != null) {
            errors.add(new ValidationError(field, "기타를 선택한 경우에만 입력할 수 있습니다."));
        }
    }
}
