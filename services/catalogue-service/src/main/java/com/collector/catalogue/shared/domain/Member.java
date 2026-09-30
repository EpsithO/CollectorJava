package com.collector.catalogue.shared.domain;

import java.util.Objects;

/** L'utilisateur connecté, tel que le métier le voit. */
public record Member(String subject, String displayName, String email) {

    public Member {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
    }
}
