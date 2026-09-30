package com.collector.catalogue.interest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.catalogue.interest.application.port.InterestRepository;
import com.collector.catalogue.interest.domain.InterestSelection;
import com.collector.catalogue.interest.domain.InterestsUpdated;
import com.collector.catalogue.interest.domain.UnknownCategoryException;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.DomainEvent;
import com.collector.catalogue.shared.domain.Member;

class UpdateInterestsTest {

    private static final UUID SNEAKERS = UUID.randomUUID();
    private static final UUID POSTERS = UUID.randomUUID();
    private static final Member BUYER = new Member(UUID.randomUUID().toString(), "Acheteur", "a@test.local");

    private final InMemoryInterests interests = new InMemoryInterests();
    private final List<DomainEvent> published = new ArrayList<>();
    private final UpdateInterests updateInterests = new UpdateInterests(
            new FixedMember(), ids -> Set.of(SNEAKERS, POSTERS), interests, published::add);

    @Test
    void replacesSelectionAndPublishesEvent() {                   // CA-1
        updateInterests.execute(BUYER, InterestSelection.of(List.of(SNEAKERS)));

        assertThat(interests.stored.categoryIds()).containsExactly(SNEAKERS);
        assertThat(published).singleElement()
                .isInstanceOfSatisfying(InterestsUpdated.class,
                        e -> assertThat(e.categoryIds()).containsExactly(SNEAKERS));
    }

    @Test
    void rejectsUnknownCategoryWithoutWriting() {                // CA-3
        var unknown = UUID.randomUUID();

        assertThatThrownBy(() -> updateInterests.execute(BUYER, InterestSelection.of(List.of(SNEAKERS, unknown))))
                .isInstanceOf(UnknownCategoryException.class);
        assertThat(interests.stored.categoryIds()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void sameSelectionPublishesNothing() {                       // CA-6
        updateInterests.execute(BUYER, InterestSelection.of(List.of(SNEAKERS, POSTERS)));
        published.clear();

        updateInterests.execute(BUYER, InterestSelection.of(List.of(POSTERS, SNEAKERS)));

        assertThat(published).isEmpty();
    }

    @Test
    void getInterestsOfUnknownMemberIsEmpty() {
        var unknownMember = new MemberDirectory() {
            @Override public UUID ensureMember(Member member) { throw new AssertionError("une lecture n'écrit pas"); }
            @Override public Optional<UUID> findId(String subject) { return Optional.empty(); }
        };

        assertThat(new GetInterests(unknownMember, interests).execute(BUYER)).isEmpty();
    }

    // --- doublures -------------------------------------------------------

    private static final class InMemoryInterests implements InterestRepository {
        InterestSelection stored = InterestSelection.empty();

        @Override public InterestSelection findFor(UUID memberId) { return stored; }
        @Override public void replace(UUID memberId, InterestSelection selection) { stored = selection; }
        @Override public List<InterestView> detailedFor(UUID memberId) { return List.of(); }
    }

    private static final class FixedMember implements MemberDirectory {
        private final UUID id = UUID.randomUUID();

        @Override public UUID ensureMember(Member member) { return id; }
        @Override public Optional<UUID> findId(String subject) { return Optional.of(id); }
    }
}
