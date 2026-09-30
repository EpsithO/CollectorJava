package com.collector.catalogue.testsupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;

public class FakeMembers implements MemberDirectory {

    private final Map<String, UUID> ids = new HashMap<>();

    /** Enregistre un membre connu et renvoie son identifiant interne. */
    public UUID register(Member member) {
        return ensureMember(member);
    }

    @Override
    public UUID ensureMember(Member member) {
        return ids.computeIfAbsent(member.subject(), subject -> UUID.randomUUID());
    }

    @Override
    public Optional<UUID> findId(String subject) {
        return Optional.ofNullable(ids.get(subject));
    }
}
