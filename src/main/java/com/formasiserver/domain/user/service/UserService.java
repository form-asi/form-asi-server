package com.formasiserver.domain.user.service;

import com.formasiserver.domain.helptime.entity.TimeAccount;
import com.formasiserver.domain.helptime.repository.TimeAccountRepository;
import com.formasiserver.domain.user.dto.request.ProfileUpdateRequest;
import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.dto.response.NicknameAvailabilityResponse;
import com.formasiserver.domain.user.dto.response.NotificationSettingsResponse;
import com.formasiserver.domain.user.dto.response.UserProfileResponse;
import com.formasiserver.domain.user.entity.ProfileChangeLog;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserProfile;
import com.formasiserver.domain.user.entity.UserProfileField;
import com.formasiserver.domain.user.entity.UserProfilePet;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.entity.profile.Field;
import com.formasiserver.domain.user.entity.profile.PetType;
import com.formasiserver.domain.user.repository.ProfileChangeLogRepository;
import com.formasiserver.domain.user.repository.UserProfileFieldRepository;
import com.formasiserver.domain.user.repository.UserProfilePetRepository;
import com.formasiserver.domain.user.repository.UserProfileRepository;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.domain.user.utils.NicknameUtils;
import com.formasiserver.domain.user.utils.ProfileOptionsValidator;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String NICKNAME_UNIQUE_INDEX = "uk_users_nickname_ignore_case";

    private final UserRepository userRepository;
    private final BirthDateValidator birthDateValidator;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileFieldRepository userProfileFieldRepository;
    private final UserProfilePetRepository userProfilePetRepository;
    private final ProfileChangeLogRepository profileChangeLogRepository;
    private final TimeAccountRepository timeAccountRepository;

    public NicknameAvailabilityResponse checkNicknameAvailability(String nickname) {
        if (!NicknameUtils.isValid(nickname)) {
            throw new ApiException(ErrorCode.INVALID_NICKNAME);
        }
        return new NicknameAvailabilityResponse(!userRepository.existsByNicknameIgnoringCase(nickname));
    }

    public CurrentUserResponse getCurrentUser(UUID userId) {
        User user = requireCurrentUser(userId);
        long helpTimeMinutes = timeAccountRepository.findByUser_UserId(user.getUserId())
                .map(TimeAccount::getBalanceMinutes)
                .orElse(0L);
        return new CurrentUserResponse(user.getUserId(), user.getNickname(), age(user), user.getStatus(),
                helpTimeMinutes, user.isNotificationEnabled());
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(UUID userId, boolean notificationEnabled) {
        User user = requireActiveUserForUpdate(userId);
        user.changeNotificationEnabled(notificationEnabled);
        return new NotificationSettingsResponse(user.isNotificationEnabled());
    }

    private User requireCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return switch (user.getStatus()) {
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case PENDING, ACTIVE -> user;
        };
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public UserProfileResponse getMyProfile(UUID userId) {
        User user = requireCurrentUser(userId);
        if (user.getStatus() == UserStatus.PENDING) {
            throw new ApiException(ErrorCode.PROFILE_REQUIRED);
        }

        return profileResponse(user.getNickname(), age(user), requireProfile(userId));
    }

    private UserProfileResponse profileResponse(@Nullable String nickname, @Nullable Integer age, UserProfile profile) {
        UUID userId = profile.getUserId();
        List<UserProfileField> selectedFields = userProfileFieldRepository.findAllByUser_UserIdOrderById(userId);
        List<Field> fields = selectedFields.stream().map(UserProfileField::getCode).toList();
        List<PetType> pets = userProfilePetRepository.findAllByUser_UserIdOrderById(userId).stream()
                .map(UserProfilePet::getCode)
                .toList();

        return new UserProfileResponse(nickname, age, profile.getGender(), profile.getOccupation(),
                profile.getOccupationOther(), fields, fieldOther(selectedFields), profile.getRegion(),
                profile.getHouseholdComposition(),
                profile.getHouseholdCompositionOther(), profile.getHouseholdSize(), profile.getMaritalStatus(),
                profile.getMaritalStatusOther(), profile.getChildrenCount(), profile.getPetExperience(), pets,
                profile.getDriverLicense());
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, ProfileUpdateRequest request) {
        User user = requireActiveUserForUpdate(userId);
        ProfileOptionsValidator.validate(request);

        UserProfile profile = requireProfile(userId);
        List<String> changedItems = new ArrayList<>();
        if (!Objects.equals(user.getNickname(), request.nickname())) {
            updateNickname(user, request.nickname());
            changedItems.add("nickname");
        }
        updateProfile(profile, request, changedItems);
        updateFields(user, request, changedItems);
        updatePets(user, request.pets(), changedItems);

        if (!changedItems.isEmpty()) {
            profileChangeLogRepository.save(ProfileChangeLog.of(user, changedItems));
        }
        return profileResponse(user.getNickname(), age(user), profile);
    }

    private User requireActiveUserForUpdate(UUID userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return switch (user.getStatus()) {
            case PENDING -> throw new ApiException(ErrorCode.PROFILE_REQUIRED);
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case ACTIVE -> user;
        };
    }

    private @Nullable Integer age(User user) {
        LocalDate birthDate = user.getBirthDate();
        return birthDate == null ? null : birthDateValidator.age(birthDate);
    }

    private UserProfile requireProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void updateNickname(User user, String nickname) {
        user.changeNickname(nickname);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && NICKNAME_UNIQUE_INDEX.equals(violation.getConstraintName())) {
                throw new ApiException(ErrorCode.NICKNAME_ALREADY_IN_USE, exception);
            }
            throw exception;
        }
    }

    private void updateProfile(UserProfile profile, ProfileUpdateRequest request, List<String> changedItems) {
        recordChange(changedItems, "gender", profile.getGender(), request.gender());
        recordChange(changedItems, "occupation", profile.getOccupation(), request.occupation());
        recordChange(changedItems, "occupationOther", profile.getOccupationOther(), request.occupationOther());
        recordChange(changedItems, "region", profile.getRegion(), request.region());
        recordChange(changedItems, "householdComposition", profile.getHouseholdComposition(),
                request.householdComposition());
        recordChange(changedItems, "householdCompositionOther", profile.getHouseholdCompositionOther(),
                request.householdCompositionOther());
        recordChange(changedItems, "householdSize", profile.getHouseholdSize(), request.householdSize());
        recordChange(changedItems, "maritalStatus", profile.getMaritalStatus(), request.maritalStatus());
        recordChange(changedItems, "maritalStatusOther", profile.getMaritalStatusOther(), request.maritalStatusOther());
        recordChange(changedItems, "childrenCount", profile.getChildrenCount(), request.childrenCount());
        recordChange(changedItems, "petExperience", profile.getPetExperience(), request.petExperience());
        recordChange(changedItems, "driverLicense", profile.getDriverLicense(), request.driverLicense());
        profile.update(request.gender(), request.occupation(), request.occupationOther(), request.region(),
                request.householdComposition(), request.householdCompositionOther(), request.householdSize(),
                request.maritalStatus(), request.maritalStatusOther(), request.childrenCount(),
                request.petExperience(), request.driverLicense());
    }

    private void updateFields(User user, ProfileUpdateRequest request, List<String> changedItems) {
        List<UserProfileField> rows = userProfileFieldRepository.findAllByUser_UserIdOrderById(user.getUserId());
        Set<Field> current = Set.copyOf(rows.stream().map(UserProfileField::getCode).toList());
        Set<Field> requested = Set.copyOf(request.fields());
        if (!current.equals(requested)) {
            changedItems.add("fields");
            userProfileFieldRepository.deleteAll(rows.stream()
                    .filter(row -> !requested.contains(row.getCode()))
                    .toList());
            userProfileFieldRepository.saveAll(request.fields().stream()
                    .filter(code -> !current.contains(code))
                    .map(code -> UserProfileField.of(user, code, code == Field.OTHER ? request.fieldOther() : null))
                    .toList());
        }
        if (!Objects.equals(fieldOther(rows), request.fieldOther())) {
            changedItems.add("fieldOther");
            if (current.contains(Field.OTHER) && requested.contains(Field.OTHER)) {
                rows.stream().filter(row -> row.getCode() == Field.OTHER)
                        .findFirst().orElseThrow().changeCustomText(request.fieldOther());
            }
        }
    }

    private void updatePets(User user, @Nullable List<PetType> pets, List<String> changedItems) {
        List<UserProfilePet> rows = userProfilePetRepository.findAllByUser_UserIdOrderById(user.getUserId());
        Set<PetType> current = Set.copyOf(rows.stream().map(UserProfilePet::getCode).toList());
        List<PetType> requested = pets == null ? List.of() : pets;
        Set<PetType> requestedCodes = Set.copyOf(requested);
        if (!current.equals(requestedCodes)) {
            changedItems.add("pets");
            userProfilePetRepository.deleteAll(rows.stream()
                    .filter(row -> !requestedCodes.contains(row.getCode()))
                    .toList());
            userProfilePetRepository.saveAll(requested.stream()
                    .filter(code -> !current.contains(code))
                    .map(code -> UserProfilePet.of(user, code))
                    .toList());
        }
    }

    private static @Nullable String fieldOther(List<UserProfileField> fields) {
        return fields.stream()
                .filter(field -> field.getCode() == Field.OTHER)
                .findFirst()
                .map(UserProfileField::getCustomText)
                .orElse(null);
    }

    private static void recordChange(List<String> changedItems, String item,
            @Nullable Object current, @Nullable Object requested) {
        if (!Objects.equals(current, requested)) {
            changedItems.add(item);
        }
    }
}
