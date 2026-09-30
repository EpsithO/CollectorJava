package com.collector.catalogue.article.adapter.in.web;

import com.collector.catalogue.article.domain.ReviewDecision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Forme du JSON ; « motif obligatoire pour rejeter » est une règle du domaine (Article.reject). */
record ReviewRequest(@NotNull ReviewDecision decision, @Size(max = 500) String reason) {
}
