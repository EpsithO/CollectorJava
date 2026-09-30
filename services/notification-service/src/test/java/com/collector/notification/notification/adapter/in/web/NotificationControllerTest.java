package com.collector.notification.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.collector.notification.notification.application.ListNotifications;
import com.collector.notification.notification.application.MarkNotificationRead;
import com.collector.notification.notification.domain.Notification;
import com.collector.notification.shared.config.SecurityConfig;
import com.collector.notification.shared.domain.NotFoundException;

@WebMvcTest(NotificationController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, NotificationControllerTest.Config.class})
class NotificationControllerTest {

    private static final UUID BUYER = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID NOTIFICATION = UUID.randomUUID();

    @TestConfiguration
    static class Config {
        @Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean ListNotifications listNotifications;
    @MockitoBean MarkNotificationRead markNotificationRead;
    @MockitoBean JwtDecoder jwtDecoder;

    private static RequestPostProcessor buyer() {
        return jwt().jwt(j -> j.subject(BUYER.toString())).authorities(new SimpleGrantedAuthority("ROLE_acheteur"));
    }

    private static Notification notification() {
        return new Notification(NOTIFICATION, BUYER, UUID.randomUUID(), "Air Jordan 1", 25_000, 24_000, "EUR", 2,
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    @Test
    void listsTheCallersNotificationsInSnakeCase() throws Exception {                   // CA-3
        when(listNotifications.execute(BUYER, false)).thenReturn(List.of(notification()));

        mvc.perform(get("/api/v1/me/notifications").with(buyer()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].type").value("PRICE_CHANGED"))
           .andExpect(jsonPath("$[0].old_price_cents").value(25000))
           .andExpect(jsonPath("$[0].new_price_cents").value(24000))
           .andExpect(jsonPath("$[0].price_drop").value(true))
           .andExpect(jsonPath("$[0].read_at").doesNotExist())
           .andExpect(jsonPath("$[0].member_id").doesNotExist());
    }

    @Test
    void unreadFilterIsPassedToTheUseCase() throws Exception {
        when(listNotifications.execute(BUYER, true)).thenReturn(List.of());

        mvc.perform(get("/api/v1/me/notifications").param("unread", "true").with(buyer()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isEmpty());
        verify(listNotifications).execute(BUYER, true);
    }

    @Test
    void markAsReadReturns204() throws Exception {                                        // CA-5
        mvc.perform(post("/api/v1/me/notifications/{id}/read", NOTIFICATION).with(buyer()))
           .andExpect(status().isNoContent());

        verify(markNotificationRead).execute(BUYER, NOTIFICATION);
    }

    @Test
    void someoneElsesOrUnknownNotificationIs404() throws Exception {                      // CA-5
        doThrow(new NotFoundException("Notification")).when(markNotificationRead).execute(any(), eq(NOTIFICATION));

        mvc.perform(post("/api/v1/me/notifications/{id}/read", NOTIFICATION).with(buyer()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(post("/api/v1/me/notifications/{id}/read", "pas-un-uuid").with(buyer()))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void requiresATokenAndTheBuyerRole() throws Exception {                               // CA-6
        mvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/notifications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_vendeur"))))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(post("/api/v1/me/notifications/{id}/read", NOTIFICATION)).andExpect(status().isUnauthorized());
        verifyNoInteractions(listNotifications, markNotificationRead);
    }

    @Test
    void unexpectedFailureIs500WithoutDetail() throws Exception {
        when(listNotifications.execute(any(), eq(false))).thenThrow(new IllegalStateException("SELECT * FROM secret"));

        mvc.perform(get("/api/v1/me/notifications").with(buyer()))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.code").value("internal_error"))
           .andExpect(jsonPath("$.detail").value("Internal server error"));
    }
}
