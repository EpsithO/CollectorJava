package com.collector.catalogue.shared.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;

@Component
class MemberJdbcAdapter implements MemberDirectory {

    private final JdbcClient jdbc;

    MemberJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Upsert : pas de course entre « chercher » et « créer ». Effet utile : la ligne
    // du membre reste verrouillée jusqu'au commit, ce qui sérialise deux
    // modifications simultanées pour un même membre.
    @Override
    public UUID ensureMember(Member member) {
        return jdbc.sql("""
                    INSERT INTO app_user (keycloak_sub, display_name, email)
                    VALUES (:sub, :name, :email)
                    ON CONFLICT (keycloak_sub) DO UPDATE SET display_name = EXCLUDED.display_name
                    RETURNING id
                    """)
                .param("sub", UUID.fromString(member.subject()))
                .param("name", member.displayName())
                .param("email", member.email())
                .query(UUID.class)
                .single();
    }

    @Override
    public Optional<UUID> findId(String subject) {
        return jdbc.sql("SELECT id FROM app_user WHERE keycloak_sub = :sub")
                .param("sub", UUID.fromString(subject))
                .query(UUID.class)
                .optional();
    }
}
