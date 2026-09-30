package com.collector.catalogue.interest.adapter.out.persistence;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.collector.catalogue.interest.application.InterestView;
import com.collector.catalogue.interest.application.port.CategoryCatalog;
import com.collector.catalogue.interest.application.port.InterestRepository;
import com.collector.catalogue.interest.domain.InterestSelection;

/**
 * JdbcClient plutôt que JPA : une table d'association sans comportement ne mérite pas
 * d'entité. Chaque adaptateur choisit sa technique, le domaine n'en sait rien.
 */
@Component
class InterestJdbcAdapter implements InterestRepository, CategoryCatalog {

    private final JdbcClient jdbc;

    InterestJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> existing(Set<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Set.of();                    // « IN () » est une erreur de syntaxe SQL
        }
        return Set.copyOf(jdbc.sql("SELECT id FROM category WHERE id IN (:ids)")
                .param("ids", categoryIds)
                .query(UUID.class)
                .list());
    }

    @Override
    public InterestSelection findFor(UUID memberId) {
        return InterestSelection.of(jdbc.sql("SELECT category_id FROM user_interest WHERE user_id = :id")
                .param("id", memberId)
                .query(UUID.class)
                .list());
    }

    // Remplacement complet dans la transaction du cas d'usage.
    @Override
    public void replace(UUID memberId, InterestSelection selection) {
        jdbc.sql("DELETE FROM user_interest WHERE user_id = :id").param("id", memberId).update();
        for (UUID categoryId : selection.categoryIds()) {
            jdbc.sql("INSERT INTO user_interest (user_id, category_id) VALUES (:member, :category)")
                    .param("member", memberId)
                    .param("category", categoryId)
                    .update();
        }
    }

    @Override
    public List<InterestView> detailedFor(UUID memberId) {
        return jdbc.sql("""
                    SELECT c.id, c.slug, c.label
                    FROM user_interest i JOIN category c ON c.id = i.category_id
                    WHERE i.user_id = :id
                    ORDER BY c.label
                    """)
                .param("id", memberId)
                .query((rs, row) -> new InterestView(rs.getObject("id", UUID.class),
                        rs.getString("slug"), rs.getString("label")))
                .list();
    }
}
