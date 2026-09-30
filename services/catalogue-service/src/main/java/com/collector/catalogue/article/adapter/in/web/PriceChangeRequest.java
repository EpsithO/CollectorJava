package com.collector.catalogue.article.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

record PriceChangeRequest(@Min(1) @Max(100_000_000) long priceCents) {
}
