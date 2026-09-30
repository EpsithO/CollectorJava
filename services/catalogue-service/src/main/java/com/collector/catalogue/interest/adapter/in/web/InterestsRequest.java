package com.collector.catalogue.interest.adapter.in.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Validation TECHNIQUE (forme du JSON) ; la validation MÉTIER (10 au plus) est dans
 * InterestSelection. Le @Size protège seulement contre un corps démesuré.
 */
record InterestsRequest(@NotNull @Size(max = 100) List<@NotNull UUID> categoryIds) {
}
