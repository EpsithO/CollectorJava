package com.collector.catalogue.article.application;

import java.util.Map;
import java.util.UUID;

public record DraftCommand(String title, String description, UUID categoryId, long priceCents,
                           long shippingCents, Map<String, Object> attributes) {
}
