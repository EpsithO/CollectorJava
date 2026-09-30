package com.collector.catalogue.interest.adapter.in.web;

import java.util.List;
import java.util.UUID;

import com.collector.catalogue.interest.application.InterestView;

record InterestResponse(UUID categoryId, String slug, String label) {

    static List<InterestResponse> from(List<InterestView> views) {
        return views.stream()
                .map(v -> new InterestResponse(v.categoryId(), v.slug(), v.label()))
                .toList();
    }
}
