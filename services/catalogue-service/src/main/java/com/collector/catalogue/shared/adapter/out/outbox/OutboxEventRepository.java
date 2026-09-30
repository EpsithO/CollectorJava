package com.collector.catalogue.shared.adapter.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

/** Écriture seule, dans la transaction métier. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
