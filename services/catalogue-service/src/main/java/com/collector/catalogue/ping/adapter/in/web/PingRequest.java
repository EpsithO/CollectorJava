package com.collector.catalogue.ping.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record PingRequest(@NotBlank @Size(max = 200) String payload) {
}
