package com.collector.catalogue.interest.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.interest.application.port.CategoryCatalog;
import com.collector.catalogue.interest.application.port.InterestRepository;
import com.collector.catalogue.interest.domain.InterestSelection;
import com.collector.catalogue.interest.domain.InterestsUpdated;
import com.collector.catalogue.interest.domain.UnknownCategoryException;
import com.collector.catalogue.shared.application.port.DomainEventPublisher;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;

@Service
public class UpdateInterests {

    private final MemberDirectory members;
    private final CategoryCatalog categories;
    private final InterestRepository interests;
    private final DomainEventPublisher events;

    public UpdateInterests(MemberDirectory members, CategoryCatalog categories,
                           InterestRepository interests, DomainEventPublisher events) {
        this.members = members;
        this.categories = categories;
        this.interests = interests;
        this.events = events;
    }

    // Une transaction : remplacement de la liste ET écriture de l'événement dans
    // l'outbox réussissent ensemble ou échouent ensemble.
    @Transactional
    public void execute(Member member, InterestSelection requested) {
        Set<UUID> unknown = new HashSet<>(requested.categoryIds());
        unknown.removeAll(categories.existing(requested.categoryIds()));
        if (!unknown.isEmpty()) {
            throw new UnknownCategoryException(unknown);          // CA-3 : on refuse avant toute écriture
        }

        UUID memberId = members.ensureMember(member);
        if (interests.findFor(memberId).sameAs(requested)) {
            return;                                               // CA-6 : rien ne change, pas d'événement
        }

        interests.replace(memberId, requested);
        // L'identité qui circule entre services est le sub Keycloak, pas l'identifiant interne.
        events.publish(new InterestsUpdated(UUID.fromString(member.subject()), List.copyOf(requested.categoryIds())));
    }
}
