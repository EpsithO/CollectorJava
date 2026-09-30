package com.collector.notification.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.notification.notification.application.port.FollowRepository;
import com.collector.notification.notification.domain.FollowChange;

/** US-029 CA-1 et CA-2 : tient à jour la copie propre des abonnements. */
@Service
public class RecordFollowChange {

    private final FollowRepository follows;

    public RecordFollowChange(FollowRepository follows) {
        this.follows = follows;
    }

    /** Idempotent ; renvoie faux si un changement plus récent était déjà enregistré. */
    @Transactional
    public boolean execute(FollowChange change) {
        return follows.apply(change);
    }
}
