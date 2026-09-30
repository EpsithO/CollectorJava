package com.collector.catalogue.interest.adapter.in.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.collector.catalogue.interest.application.GetInterests;
import com.collector.catalogue.interest.application.UpdateInterests;
import com.collector.catalogue.interest.domain.InterestSelection;
import com.collector.catalogue.shared.adapter.in.web.Members;
import com.collector.catalogue.shared.domain.Member;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me/interests")
@PreAuthorize("hasRole('acheteur')")                 // CA-5 : 403 sans le rôle
class InterestController {

    private final GetInterests getInterests;
    private final UpdateInterests updateInterests;

    InterestController(GetInterests getInterests, UpdateInterests updateInterests) {
        this.getInterests = getInterests;
        this.updateInterests = updateInterests;
    }

    @GetMapping
    List<InterestResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return InterestResponse.from(getInterests.execute(Members.from(jwt)));
    }

    // PUT et non POST : remplacement complet, idempotent (CA-6).
    @PutMapping
    List<InterestResponse> replace(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InterestsRequest request) {
        Member member = Members.from(jwt);
        updateInterests.execute(member, InterestSelection.of(request.categoryIds()));   // règles : domaine
        return InterestResponse.from(getInterests.execute(member));
    }
}
