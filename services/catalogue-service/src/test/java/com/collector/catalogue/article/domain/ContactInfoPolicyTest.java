package com.collector.catalogue.article.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContactInfoPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Écrivez-moi à jean.dupont@gmail.com pour un meilleur prix",
            "contact : jean (at) gmail [dot] com",
            "jean arobase gmail . com",
            "JEAN@GMAIL.COM",
            "Appelez le 06 12 34 56 78",
            "tel 06.12.34.56.78",
            "0612345678 svp",
            "+33 6 12 34 56 78",
            "0033 612345678",
            "+1 415 555 2671"})
    void detectsContactInfo(String text) {
        assertThat(ContactInfoPolicy.containsContactInfo(text)).as(text).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Air Jordan 1 Retro High OG 1985 taille 42",
            "Édition limitée à 1500 exemplaires, année 1990",
            "Prix : 250 € frais de port inclus",
            "Référence 2023-0456 état neuf",
            "Figurine Star Wars 12 cm, boîte d'origine",
            "Poster dédicacé 40 x 60 cm"})
    void doesNotFlagOrdinaryText(String text) {
        assertThat(ContactInfoPolicy.containsContactInfo(text)).as(text).isFalse();
    }

    @org.junit.jupiter.api.Test
    void nullIsHarmless() {
        assertThat(ContactInfoPolicy.containsContactInfo(null)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void requireNoneThrowsWithStableCode() {
        assertThatThrownBy(() -> ContactInfoPolicy.requireNone("mon mail : a@b.fr"))
                .isInstanceOf(ContactInfoForbiddenException.class)
                .extracting("code").isEqualTo("contact_info_forbidden");
    }
}
