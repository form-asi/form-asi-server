package com.formasiserver.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formasiserver.domain.auth.jwt.JwtProvider;
import com.formasiserver.domain.auth.repository.AuthTokenRedisRepository;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.domain.user.entity.UserRole;
import com.formasiserver.domain.user.entity.UserStatus;
import com.formasiserver.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = "AUTH_ALLOWED_ORIGINS=https://form.example,http://localhost:5173")
@AutoConfigureMockMvc
@Import(ApiSecurityTest.ProbeController.class)
class ApiSecurityTest {

    private static final String ME = "/api/v1/users/me";
    private static final String ORIGIN = "http://localhost:5173";
    private static final UUID USER_ID = UUID.fromString("0885b3b6-899d-4c28-bdcf-ba73d794cdca");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtProvider jwtProvider;
    @MockitoBean
    private AuthTokenRedisRepository tokenRepository;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void rejectsMissingAccessAndIgnoresSessionAuthentication() throws Exception {
        UsernamePasswordAuthenticationToken sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(USER_ID, UserRole.ADMIN, UserStatus.ACTIVE), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(sessionAuthentication));

        mvc.perform(get(ME)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mvc.perform(get(ME).session(session).cookie(new Cookie("JSESSIONID", session.getId())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tokenRepository, userRepository);
    }

    @Test
    void rejectsRefreshAndMalformedTokensBeforeLookingUpTheUser() throws Exception {
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issueRefreshToken(USER_ID).value()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tokenRepository, userRepository);
    }

    @Test
    void rejectsBlockedAccessWithoutLookingUpTheUser() throws Exception {
        when(tokenRepository.isAccessBlocked(any())).thenReturn(true);
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsAccessIfUserNoLongerExists() throws Exception {
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verify(userRepository).findById(USER_ID);
    }

    @ParameterizedTest
    @CsvSource({"SUSPENDED, ACCOUNT_SUSPENDED", "WITHDRAWN, ACCOUNT_WITHDRAWN"})
    void usesCurrentAccountStatus(UserStatus userStatus, String error) throws Exception {
        member(userStatus, UserRole.USER);
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(error));
    }

    @ParameterizedTest
    @CsvSource({"PENDING, USER", "ACTIVE, ADMIN", "ACTIVE, USER"})
    void injectsPrincipalFromJwtAndDatabaseWithoutReadingRefresh(UserStatus userStatus, UserRole role)
            throws Exception {
        member(userStatus, role);
        UUID otherUser = UUID.randomUUID();
        MvcResult result = mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, access())
                        .header("X-User-Id", otherUser).param("userId", otherUser.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.status").value(userStatus.name()))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        verify(tokenRepository).isAccessBlocked(any());
        verifyNoMoreInteractions(tokenRepository);
    }

    @Test
    void pendingMemberCanOnlyUseTheListedMethodsAndPaths() throws Exception {
        member(UserStatus.PENDING, UserRole.USER);
        String access = access();
        mvc.perform(get("/api/v1/users/profile-options").header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/nickname-availability").header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/me/onboarding").header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isOk());
        mvc.perform(post(ME).header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PROFILE_REQUIRED"));
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE, USER", "ACTIVE, ADMIN"})
    void unlistedRoutesAreDeniedEvenForAnActiveMember(UserStatus userStatus, UserRole role) throws Exception {
        member(userStatus, role);
        mvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, access()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void acceptsCredentialedPreflightOnlyFromConfiguredOrigins() throws Exception {
        mvc.perform(options(ME).header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mvc.perform(options(ME).header(HttpHeaders.ORIGIN, "https://unknown.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(tokenRepository, userRepository);
    }

    @Test
    void authEndpointsAreDeniedUntilConnected() throws Exception {
        member(UserStatus.ACTIVE, UserRole.USER);
        String access = access();
        mvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, access))
                .andExpect(status().isForbidden());
    }

    private void member(UserStatus userStatus, UserRole role) {
        User user = User.of("1234", LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(user, "userId", USER_ID);
        ReflectionTestUtils.setField(user, "status", userStatus);
        ReflectionTestUtils.setField(user, "role", role);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private String access() {
        return "Bearer " + jwtProvider.issueAccessToken(USER_ID).value();
    }

    @TestComponent
    @RestController
    static class ProbeController {

        @GetMapping({ME, "/api/v1/users/profile-options", "/api/v1/users/nickname-availability", "/api/v1/admin/probe"})
        public AuthenticatedUser principal(@AuthenticationPrincipal(errorOnInvalidType = true) AuthenticatedUser user) {
            return user;
        }

        @PostMapping("/api/v1/users/me/onboarding")
        public AuthenticatedUser onboarding(@AuthenticationPrincipal(errorOnInvalidType = true) AuthenticatedUser user) {
            return user;
        }

    }
}
