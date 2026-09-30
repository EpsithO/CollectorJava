package com.collector.notification.notification.adapter.in.web;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.collector.notification.notification.application.ListNotifications;
import com.collector.notification.notification.application.MarkNotificationRead;

/** US-029 : l'espace de notifications de l'acheteur. Le membre est toujours celui du jeton, jamais un paramètre. */
@RestController
@RequestMapping("/api/v1/me/notifications")
@PreAuthorize("hasRole('acheteur')")                 // CA-6 : 403 sans le rôle
class NotificationController {

    private final ListNotifications listNotifications;
    private final MarkNotificationRead markNotificationRead;

    NotificationController(ListNotifications listNotifications, MarkNotificationRead markNotificationRead) {
        this.listNotifications = listNotifications;
        this.markNotificationRead = markNotificationRead;
    }

    @GetMapping
    List<NotificationResponse> list(@RequestParam(name = "unread", defaultValue = "false") boolean unread,
                                    @AuthenticationPrincipal Jwt jwt) {
        return listNotifications.execute(memberOf(jwt), unread).stream().map(NotificationResponse::from).toList();
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        markNotificationRead.execute(memberOf(jwt), id);
    }

    // Le sub Keycloak est l'identité commune à tous les services (celle des événements).
    private static UUID memberOf(Jwt jwt) {
        return UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "sub"));
    }
}
