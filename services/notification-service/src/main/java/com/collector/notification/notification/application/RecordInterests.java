package com.collector.notification.notification.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.notification.notification.application.port.InterestRepository;

/** Copie les centres d'intérêt (interests.updated) : base de la future notification d'un nouvel article. */
@Service
public class RecordInterests {

    private final InterestRepository interests;

    public RecordInterests(InterestRepository interests) {
        this.interests = interests;
    }

    @Transactional
    public void execute(UUID memberId, Set<UUID> categoryIds) {
        interests.replace(memberId, categoryIds);
    }
}
