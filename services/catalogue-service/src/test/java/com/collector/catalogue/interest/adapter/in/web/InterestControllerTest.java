package com.collector.catalogue.interest.adapter.in.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.collector.catalogue.interest.application.GetInterests;
import com.collector.catalogue.interest.application.UpdateInterests;
import com.collector.catalogue.interest.domain.TooManyInterestsException;
import com.collector.catalogue.shared.config.SecurityConfig;

@WebMvcTest(InterestController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class InterestControllerTest {

    private static final String BUYER = "33333333-3333-4333-8333-333333333333";

    @Autowired MockMvc mvc;
    @MockitoBean GetInterests getInterests;
    @MockitoBean UpdateInterests updateInterests;
    @MockitoBean JwtDecoder jwtDecoder;

    private static RequestPostProcessor buyer() {
        return jwt().jwt(j -> j.subject(BUYER).claim("name", "Acheteur Un").claim("email", "a1@collector.local"))
                    .authorities(new SimpleGrantedAuthority("ROLE_acheteur"));
    }

    @Test
    void withoutTokenIs401() throws Exception {                             // CA-5
        mvc.perform(get("/api/v1/me/interests")).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutBuyerRoleIs403() throws Exception {                         // CA-5
        mvc.perform(get("/api/v1/me/interests").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(put("/api/v1/me/interests").with(buyer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_ids\": [\"pas-un-uuid\"]}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void tooManyInterestsIs422() throws Exception {                         // CA-4 : règle du domaine, traduite en HTTP
        String elevenIds = Stream.generate(() -> "\"" + UUID.randomUUID() + "\"").limit(11)
                .collect(Collectors.joining(",", "[", "]"));

        mvc.perform(put("/api/v1/me/interests").with(buyer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_ids\": " + elevenIds + "}"))
           .andExpect(status().isUnprocessableContent())
           .andExpect(jsonPath("$.code").value("too_many_interests"));
        verifyNoInteractions(updateInterests);                              // refusé avant le cas d'usage
    }

    @Test
    void domainErrorFromTheUseCaseIsTranslated() throws Exception {
        org.mockito.Mockito.doThrow(new TooManyInterestsException(11)).when(updateInterests)
                .execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        mvc.perform(put("/api/v1/me/interests").with(buyer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_ids\": []}"))
           .andExpect(status().isUnprocessableContent());
    }

    @Test
    void unknownFieldIs400() throws Exception {
        mvc.perform(put("/api/v1/me/interests").with(buyer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_ids\": [], \"extra\": 1}"))
           .andExpect(status().isBadRequest());
    }
}
