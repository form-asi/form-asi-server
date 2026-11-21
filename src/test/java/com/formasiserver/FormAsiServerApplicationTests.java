package com.formasiserver;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "AUTH_ALLOWED_ORIGINS=")
@AutoConfigureMockMvc
class FormAsiServerApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoads() {
    }

    @Test
    void publishesSwaggerContractsForAuthAndOnboarding() throws Exception {
        mvc.perform(get("/api-docs")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.refreshCookie.name").value("refresh_token"))
                .andExpect(jsonPath("$.components.schemas.BaseResponse.properties.success.type").value("boolean"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security[0].refreshCookie").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['200'].headers['Set-Cookie']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security[0].refreshCookie").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/users/profile-options'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/nickname-availability'].get.parameters[0].name").value("nickname"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me/onboarding'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/onboarding'].post.requestBody.content['application/json']"
                        + ".examples['신규 회원'].value.birthDate").value("2000-01-01"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me/onboarding'].post.responses['403'].description")
                        .value(containsString("만 14세 미만")))
                .andExpect(jsonPath("$.paths['/api/v1/users/me/onboarding'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.OnboardingRequest.properties.birthDate.format").value("date"));
    }

    @Test
    void deniesTheOldOAuthEntryAndPostRequestsToTheGetEndpoints() throws Exception {
        mvc.perform(get("/oauth2/authorization/kakao").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/auth/login/kakao").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(post("/login/oauth2/code/kakao").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void corsDeniesCrossOriginWhenNoOriginsAreConfigured() throws Exception {
        mvc.perform(options("/api/v1/users/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

}
