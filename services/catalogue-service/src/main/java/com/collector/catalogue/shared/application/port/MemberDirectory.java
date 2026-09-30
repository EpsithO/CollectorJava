package com.collector.catalogue.shared.application.port;

import java.util.Optional;
import java.util.UUID;

import com.collector.catalogue.shared.domain.Member;

public interface MemberDirectory {

    /** Identifiant interne du membre, créé à sa première action d'écriture. */
    UUID ensureMember(Member member);

    /** Lecture seule : ne crée rien (une requête GET ne doit pas écrire). */
    Optional<UUID> findId(String subject);
}
