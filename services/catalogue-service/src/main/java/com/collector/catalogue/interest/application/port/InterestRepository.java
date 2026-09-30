package com.collector.catalogue.interest.application.port;

import java.util.List;
import java.util.UUID;

import com.collector.catalogue.interest.application.InterestView;
import com.collector.catalogue.interest.domain.InterestSelection;

public interface InterestRepository {

    InterestSelection findFor(UUID memberId);

    void replace(UUID memberId, InterestSelection selection);

    /** Lecture pour l'affichage, triée par libellé. */
    List<InterestView> detailedFor(UUID memberId);
}
