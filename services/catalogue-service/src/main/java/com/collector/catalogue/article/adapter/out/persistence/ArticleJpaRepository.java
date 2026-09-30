package com.collector.catalogue.article.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.collector.catalogue.article.domain.ArticleStatus;

interface ArticleJpaRepository extends JpaRepository<ArticleEntity, UUID> {

    Page<ArticleEntity> findByStatusOrderByPublishedAtDesc(ArticleStatus status, Pageable pageable);

    Page<ArticleEntity> findByStatusAndCategoryIdOrderByPublishedAtDesc(ArticleStatus status, UUID categoryId,
                                                                        Pageable pageable);

    List<ArticleEntity> findByStatusOrderByCreatedAtAsc(ArticleStatus status);

    List<ArticleEntity> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    List<ArticleEntity> findBySellerIdAndStatusOrderByCreatedAtDesc(UUID sellerId, ArticleStatus status);
}
