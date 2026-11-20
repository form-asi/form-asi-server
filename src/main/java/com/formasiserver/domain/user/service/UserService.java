package com.formasiserver.domain.user.service;

import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.repository.UserRepository;
import com.formasiserver.global.exception.ApiException;
import com.formasiserver.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BirthDateValidator birthDateValidator;

    public CurrentUserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return switch (user.getStatus()) {
            case SUSPENDED -> throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> throw new ApiException(ErrorCode.ACCOUNT_WITHDRAWN);
            case PENDING, ACTIVE -> new CurrentUserResponse(user.getUserId(), user.getNickname(),
                    birthDateValidator.age(user.getBirthDate()), user.getStatus());
        };
    }
}
