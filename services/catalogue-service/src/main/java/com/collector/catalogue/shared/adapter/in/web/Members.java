package com.collector.catalogue.shared.adapter.in.web;

import java.util.Objects;
import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;

import com.collector.catalogue.shared.domain.Member;

/** Le jeton (technique) devient un Member (métier) : le domaine ne voit jamais le Jwt. */
public final class Members {

    private Members() {
    }

    public static Member from(Jwt jwt) {
        // Un jeton sans sub n'identifie personne : on refuse plutôt que de fabriquer « null@… ».
        String subject = Objects.requireNonNull(jwt.getSubject(), "sub");
        String name = Optional.ofNullable(jwt.getClaimAsString("name"))
                .or(() -> Optional.ofNullable(jwt.getClaimAsString("preferred_username")))
                .orElse("Utilisateur");
        // app_user.email est obligatoire et unique : adresse technique si le jeton n'en porte pas.
        String email = Optional.ofNullable(jwt.getClaimAsString("email"))
                .orElse(subject + "@users.collector.local");
        return new Member(subject, name, email);
    }

    /** Jeton facultatif (routes publiques) : visiteur anonyme = aucun membre. */
    public static Optional<Member> optional(Jwt jwt) {
        return Optional.ofNullable(jwt).map(Members::from);
    }
}
