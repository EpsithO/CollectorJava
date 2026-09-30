package com.collector.catalogue.ping.adapter.in.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.collector.catalogue.ping.application.CreatePing;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/pings")
@Profile({"dev", "recette"})               // démonstration : absent en production
class PingController {

    private final CreatePing createPing;

    PingController(CreatePing createPing) {
        this.createPing = createPing;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PingResponse create(@Valid @RequestBody PingRequest request) {
        return PingResponse.from(createPing.execute(request.payload()));
    }
}
