package com.collector.catalogue.shared.config;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Rôles Keycloak (realm_access.roles) vers ROLE_acheteur, ROLE_vendeur, ROLE_admin. */
final class KeycloakRealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<?> roles = Optional.ofNullable(jwt.getClaimAsMap("realm_access"))
                .map(realm -> realm.get("roles"))
                .filter(Collection.class::isInstance)
                .map(Collection.class::cast)
                .orElse(List.of());
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
