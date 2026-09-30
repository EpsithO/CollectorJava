package com.collector.catalogue.article.adapter.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

record PhotoUploadRequest(@NotBlank String contentType, @Min(1) long sizeBytes) {
}
