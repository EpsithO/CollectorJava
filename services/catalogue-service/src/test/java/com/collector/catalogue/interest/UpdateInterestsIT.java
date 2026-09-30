package com.collector.catalogue.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.collector.catalogue.AbstractIntegrationTest;
import com.collector.catalogue.interest.application.GetInterests;
import com.collector.catalogue.interest.application.InterestView;
import com.collector.catalogue.interest.application.UpdateInterests;
import com.collector.catalogue.interest.domain.InterestSelection;
import com.collector.catalogue.interest.domain.UnknownCategoryException;
import com.collector.catalogue.shared.domain.Member;

/** Vérifie en une fois l'adaptateur, la transaction et l'outbox (à travers le cas d'usage). */
class UpdateInterestsIT extends AbstractIntegrationTest {

    @Autowired UpdateInterests updateInterests;
    @Autowired GetInterests getInterests;
    @Autowired JdbcClient jdbc;

    // Identité unique par test : app_user.email est unique, et la base est partagée par la classe.
    private final String subject = UUID.randomUUID().toString();
    private final Member buyer = new Member(subject, "Acheteur IT", subject + "@test.local");

    @Test
    void storesSelectionAndWritesEventInOutbox() {
        List<UUID> twoCategories = jdbc.sql("SELECT id FROM category ORDER BY label LIMIT 2")
                .query(UUID.class).list();

        updateInterests.execute(buyer, InterestSelection.of(twoCategories));

        assertThat(getInterests.execute(buyer))
                .extracting(InterestView::categoryId)
                .containsExactlyElementsOf(twoCategories);            // triés par libellé
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE routing_key = 'interests.updated' "
                        + "AND payload->'data'->>'member_id' = (SELECT id::text FROM app_user WHERE keycloak_sub = :sub)")
                .param("sub", UUID.fromString(subject))
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void unknownCategoryRollsBackEverything() {
        assertThatThrownBy(() -> updateInterests.execute(buyer, InterestSelection.of(List.of(UUID.randomUUID()))))
                .isInstanceOf(UnknownCategoryException.class);

        assertThat(getInterests.execute(buyer)).isEmpty();
    }
}
