package com.formasiserver.domain.user.service;

import com.formasiserver.domain.user.dto.request.OnboardingRequest;
import com.formasiserver.domain.user.dto.request.ProfileUpdateRequest;
import com.formasiserver.domain.user.dto.response.OnboardingResponse;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserProfile;
import com.formasiserver.domain.user.entity.UserProfileField;
import com.formasiserver.domain.user.entity.UserProfilePet;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.entity.profile.Field;
import com.formasiserver.domain.user.repository.UserProfileFieldRepository;
import com.formasiserver.domain.user.repository.UserProfilePetRepository;
import com.formasiserver.domain.user.repository.UserProfileRepository;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.domain.user.utils.ProfileOptionsValidator;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final String PROFILE_PRIMARY_KEY = "user_profile_pkey";
    private static final String NICKNAME_UNIQUE_INDEX = "uk_users_nickname_ignore_case";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileFieldRepository userProfileFieldRepository;
    private final UserProfilePetRepository userProfilePetRepository;
    private final BirthDateValidator birthDateValidator;

    @Transactional
    public OnboardingResponse complete(UUID userId, OnboardingRequest request) {
        User user = requirePendingUser(userId);
        LocalDate birthDate = user.getBirthDate();
        if (birthDate == null) {
            birthDate = request.birthDate();
        }
        birthDateValidator.validate(birthDate);
        ProfileUpdateRequest profile = request.profile();
        ProfileOptionsValidator.validate(profile);

        try {
            saveProfile(user, profile);
            if (userRepository.activateOnboarding(userId, request.nickname(), birthDate, Instant.now()) == 0) {
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

    private void saveProfile(User user, ProfileUpdateRequest request) {
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
}
