package com.formasiserver.domain.user.controller;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.service.UserService;
import com.formasiserver.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(
            @AuthenticationPrincipal(errorOnInvalidType = true) AuthenticatedUser principal) {
        return ApiResponse.success(userService.getCurrentUser(principal.userId()));
    }
}
