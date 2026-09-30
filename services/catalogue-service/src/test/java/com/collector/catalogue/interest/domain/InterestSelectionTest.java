package com.collector.catalogue.interest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class InterestSelectionTest {

    private static final UUID SNEAKERS = UUID.randomUUID();
    private static final UUID POSTERS = UUID.randomUUID();

    @Test
    void ignoresDuplicates() {
        assertThat(InterestSelection.of(List.of(SNEAKERS, SNEAKERS, POSTERS)).categoryIds())
                .containsExactlyInAnyOrder(SNEAKERS, POSTERS);
    }

    @Test
    void acceptsTenInterestsButNotEleven() {
        assertThatCode(() -> InterestSelection.of(randomIds(10))).doesNotThrowAnyException();
        assertThatThrownBy(() -> InterestSelection.of(randomIds(11)))
                .isInstanceOf(TooManyInterestsException.class)
                .extracting("code").isEqualTo("too_many_interests");
    }

    @Test
    void emptySelectionIsAllowed() {
        assertThat(InterestSelection.of(List.of()).categoryIds()).isEmpty();
        assertThat(InterestSelection.empty().categoryIds()).isEmpty();
    }

    @Test
    void comparisonIgnoresOrder() {
        assertThat(InterestSelection.of(List.of(SNEAKERS, POSTERS))
                .sameAs(InterestSelection.of(List.of(POSTERS, SNEAKERS)))).isTrue();
        assertThat(InterestSelection.of(List.of(SNEAKERS)).sameAs(InterestSelection.of(List.of(POSTERS)))).isFalse();
    }

    @Test
    void isImmutable() {
        var ids = new HashSet<>(Set.of(SNEAKERS));
        var selection = new InterestSelection(ids);
        ids.add(POSTERS);                                                // modifier l'original…
        assertThat(selection.categoryIds()).containsExactly(SNEAKERS);   // …ne change pas la sélection
    }

    private static List<UUID> randomIds(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }
}
