package com.collector.catalogue.interest.application;

import java.util.UUID;

/** Modèle de lecture : ce que l'écran affiche. */
public record InterestView(UUID categoryId, String slug, String label) {
}
