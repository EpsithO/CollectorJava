package com.collector.catalogue.article.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PhotoJpaRepository extends JpaRepository<PhotoEntity, UUID> {

    List<PhotoEntity> findByArticleIdInOrderByArticleIdAscPositionAsc(Collection<UUID> articleIds);
}
