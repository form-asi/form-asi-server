package com.formasiserver.domain.user.service;

import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.dto.response.NicknameAvailabilityResponse;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.domain.user.utils.NicknameUtils;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BirthDateValidator birthDateValidator;

    public NicknameAvailabilityResponse checkNicknameAvailability(String nickname) {
        if (!NicknameUtils.isValid(nickname)) {
            throw new ApiException(ErrorCode.INVALID_NICKNAME);
        }
        return new NicknameAvailabilityResponse(!userRepository.existsByNicknameIgnoringCase(nickname));
    }

    public CurrentUserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        LocalDate birthDate = user.getBirthDate();
        return switch (user.getStatus()) {
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case PENDING, ACTIVE -> new CurrentUserResponse(user.getUserId(), user.getNickname(),
                    birthDate == null ? null : birthDateValidator.age(birthDate), user.getStatus());
        };
    }
}
