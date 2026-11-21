package com.formasiserver.domain.user.controller;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.user.dto.request.OnboardingRequest;
import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.dto.response.NicknameAvailabilityResponse;
import com.formasiserver.domain.user.dto.response.OnboardingResponse;
import com.formasiserver.domain.user.dto.response.ProfileOptionsResponse;
import com.formasiserver.domain.user.service.OnboardingService;
import com.formasiserver.domain.user.service.UserService;
import com.formasiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final OnboardingService onboardingService;

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(userService.getCurrentUser(principal.userId()));
    }

    @GetMapping("/profile-options")
    public ApiResponse<ProfileOptionsResponse> profileOptions() {
        return ApiResponse.success(ProfileOptionsResponse.fromOptions());
    }

    @GetMapping("/nickname-availability")
    public ApiResponse<NicknameAvailabilityResponse> nicknameAvailability(@RequestParam String nickname) {
        return ApiResponse.success(userService.checkNicknameAvailability(nickname));
    }

    @PostMapping("/me/onboarding")
    public ApiResponse<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody OnboardingRequest request) {
        return ApiResponse.success(onboardingService.complete(principal.userId(), request));
    }
}
