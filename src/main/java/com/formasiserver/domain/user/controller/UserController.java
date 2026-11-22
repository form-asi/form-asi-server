package com.formasiserver.domain.user.controller;

import com.formasiserver.domain.auth.security.AuthenticatedUser;
import com.formasiserver.domain.user.dto.request.OnboardingRequest;
import com.formasiserver.domain.user.dto.request.ProfileUpdateRequest;
import com.formasiserver.domain.user.dto.response.CurrentUserResponse;
import com.formasiserver.domain.user.dto.response.NicknameAvailabilityResponse;
import com.formasiserver.domain.user.dto.response.OnboardingResponse;
import com.formasiserver.domain.user.dto.response.ProfileOptionsResponse;
import com.formasiserver.domain.user.dto.response.UserProfileResponse;
import com.formasiserver.domain.user.service.OnboardingService;
import com.formasiserver.domain.user.service.UserService;
import com.formasiserver.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController implements UserApi {

    private final UserService userService;
    private final OnboardingService onboardingService;

    @Override
    @GetMapping("/me")
    public BaseResponse<CurrentUserResponse> me(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return BaseResponse.success(userService.getCurrentUser(principal.userId()));
    }

    @Override
    @GetMapping("/me/profile")
    public BaseResponse<UserProfileResponse> myProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return BaseResponse.success(userService.getMyProfile(principal.userId()));
    }

    @Override
    @PutMapping("/me/profile")
    public BaseResponse<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return BaseResponse.success(userService.updateMyProfile(principal.userId(), request));
    }

    @Override
    @GetMapping("/profile-options")
    public BaseResponse<ProfileOptionsResponse> profileOptions() {
        return BaseResponse.success(ProfileOptionsResponse.fromOptions());
    }

    @Override
    @GetMapping("/nickname-availability")
    public BaseResponse<NicknameAvailabilityResponse> nicknameAvailability(@RequestParam String nickname) {
        return BaseResponse.success(userService.checkNicknameAvailability(nickname));
    }

    @Override
    @PostMapping("/me/onboarding")
    public BaseResponse<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody OnboardingRequest request) {
        return BaseResponse.success(onboardingService.complete(principal.userId(), request));
    }
}
