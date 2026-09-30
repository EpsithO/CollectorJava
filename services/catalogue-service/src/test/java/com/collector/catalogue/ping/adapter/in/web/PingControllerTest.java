package com.collector.catalogue.ping.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.collector.catalogue.ping.application.CreatePing;
import com.collector.catalogue.ping.domain.Ping;
import com.collector.catalogue.shared.config.SecurityConfig;

@WebMvcTest(PingController.class)
@ActiveProfiles({"test", "dev"})
@Import(SecurityConfig.class)
class PingControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CreatePing createPing;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void createsAPingWithoutToken() throws Exception {
        when(createPing.execute("bonjour")).thenReturn(new Ping(7L, "bonjour", Instant.parse("2026-01-01T00:00:00Z")));

        mvc.perform(post("/api/v1/pings").contentType(MediaType.APPLICATION_JSON).content("{\"payload\":\"bonjour\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(7))
           .andExpect(jsonPath("$.created_at").value("2026-01-01T00:00:00Z"));
    }

    @Test
    void blankPayloadIs400WithoutTechnicalDetail() throws Exception {
        mvc.perform(post("/api/v1/pings").contentType(MediaType.APPLICATION_JSON).content("{\"payload\":\"  \"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_request"))
           .andExpect(jsonPath("$.fields[0]").value("payload"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/v1/pings").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void unexpectedFailureIs500WithoutDetail() throws Exception {
        when(createPing.execute("boom")).thenThrow(new IllegalStateException("SELECT * FROM secret_table"));

        mvc.perform(post("/api/v1/pings").contentType(MediaType.APPLICATION_JSON).content("{\"payload\":\"boom\"}"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.code").value("internal_error"))
           .andExpect(jsonPath("$.detail").value("Internal server error"));
    }
}
