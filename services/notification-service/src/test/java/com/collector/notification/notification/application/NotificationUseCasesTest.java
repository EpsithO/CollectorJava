package com.collector.notification.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.collector.notification.notification.domain.FollowChange;
import com.collector.notification.notification.domain.Notification;
import com.collector.notification.notification.domain.PriceChange;
import com.collector.notification.shared.domain.NotFoundException;
import com.collector.notification.testsupport.InMemoryStore;

/** US-029 : les cas d'usage avec une doublure des ports, sans Spring, sans base, sans broker. */
class NotificationUseCasesTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ARTICLE = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    private final InMemoryStore store = new InMemoryStore();
    private final Clock clock = Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC);
    private final NotifyPriceChange notifyPriceChange = new NotifyPriceChange(store, store, store, clock);
    private final RecordFollowChange recordFollowChange = new RecordFollowChange(store);
    private final ListNotifications listNotifications = new ListNotifications(store);
    private final MarkNotificationRead markRead = new MarkNotificationRead(store, clock);

    private PriceChange price(long version, long oldPrice, long newPrice) {
        return new PriceChange(ARTICLE, version, "Air Jordan 1", oldPrice, newPrice, "EUR");
    }

    private void follow(UUID member, boolean following, Instant at) {
        recordFollowChange.execute(new FollowChange(member, ARTICLE, following, at));
    }

    @Test
    void everyFollowerReceivesANotificationWithOldAndNewPrice() {             // CA-3
        follow(ALICE, true, T0);
        follow(BOB, true, T0);

        int created = notifyPriceChange.execute(price(2, 25_000, 24_000));

        assertThat(created).isEqualTo(2);
        Notification alice = listNotifications.execute(ALICE, false).getFirst();
        assertThat(alice.oldPriceCents()).isEqualTo(25_000);
        assertThat(alice.newPriceCents()).isEqualTo(24_000);
        assertThat(alice.isPriceDrop()).isTrue();
        assertThat(alice.articleTitle()).isEqualTo("Air Jordan 1");
        assertThat(alice.isRead()).isFalse();
        assertThat(listNotifications.execute(BOB, false)).hasSize(1);
    }

    @Test
    void aPriceRiseIsNotADrop() {
        follow(ALICE, true, T0);

        notifyPriceChange.execute(price(2, 24_000, 26_000));

        assertThat(listNotifications.execute(ALICE, false).getFirst().isPriceDrop()).isFalse();
    }

    @Test
    void nobodyIsNotifiedWithoutFollowers() {
        assertThat(notifyPriceChange.execute(price(2, 25_000, 24_000))).isZero();
        assertThat(store.notifications).isEmpty();
    }

    @Test
    void someoneWhoStoppedFollowingIsNotNotified() {
        follow(ALICE, true, T0);
        follow(ALICE, false, T0.plusSeconds(10));

        assertThat(notifyPriceChange.execute(price(2, 25_000, 24_000))).isZero();
    }

    @Test
    void theSameEventDeliveredTwiceCreatesASingleNotification() {             // livraison au moins une fois
        follow(ALICE, true, T0);

        assertThat(notifyPriceChange.execute(price(2, 25_000, 24_000))).isEqualTo(1);
        assertThat(notifyPriceChange.execute(price(2, 25_000, 24_000))).isZero();

        assertThat(listNotifications.execute(ALICE, false)).hasSize(1);
    }

    @Test
    void invertedEventsNotifyOnlyTheLatestPrice() {                            // CA-4
        follow(ALICE, true, T0);

        // Le vendeur passe de 25 000 à 24 000 (v2) puis à 23 000 (v3), mais v3 arrive la première.
        assertThat(notifyPriceChange.execute(price(3, 24_000, 23_000))).isEqualTo(1);
        assertThat(notifyPriceChange.execute(price(2, 25_000, 24_000))).isZero();

        assertThat(listNotifications.execute(ALICE, false)).singleElement()
                .satisfies(n -> assertThat(n.newPriceCents()).isEqualTo(23_000));
    }

    @Test
    void theMostRecentFollowChangeWinsWhateverTheArrivalOrder() {
        // Le « je ne suis plus » (T0+10) arrive avant le « je suis » (T0) : l'abonné ne doit pas revenir.
        assertThat(recordFollowChange.execute(new FollowChange(ALICE, ARTICLE, false, T0.plusSeconds(10)))).isTrue();
        assertThat(recordFollowChange.execute(new FollowChange(ALICE, ARTICLE, true, T0))).isFalse();

        assertThat(store.followersOf(ARTICLE)).isEmpty();
    }

    @Test
    void followingTwiceIsIdempotent() {
        follow(ALICE, true, T0);
        assertThat(recordFollowChange.execute(new FollowChange(ALICE, ARTICLE, true, T0))).isFalse();
        assertThat(store.followersOf(ARTICLE)).containsExactly(ALICE);
    }

    @Test
    void aMemberNeverSeesSomeoneElsesNotifications() {                         // CA-5
        follow(ALICE, true, T0);
        notifyPriceChange.execute(price(2, 25_000, 24_000));
        UUID alicesNotification = listNotifications.execute(ALICE, false).getFirst().id();

        assertThat(listNotifications.execute(BOB, false)).isEmpty();
        assertThatThrownBy(() -> markRead.execute(BOB, alicesNotification)).isInstanceOf(NotFoundException.class);
        assertThat(listNotifications.execute(ALICE, true)).hasSize(1);          // toujours non lue
    }

    @Test
    void markingAsReadHidesItFromTheUnreadListAndIsIdempotent() {              // CA-5
        follow(ALICE, true, T0);
        notifyPriceChange.execute(price(2, 25_000, 24_000));
        UUID id = listNotifications.execute(ALICE, true).getFirst().id();

        markRead.execute(ALICE, id);
        markRead.execute(ALICE, id);

        assertThat(listNotifications.execute(ALICE, true)).isEmpty();
        assertThat(listNotifications.execute(ALICE, false)).singleElement()
                .satisfies(n -> assertThat(n.readAt()).isEqualTo(T0.plusSeconds(60)));
        assertThatThrownBy(() -> markRead.execute(ALICE, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void interestsAreCopiedAsAWholeSet() {
        UUID sneakers = UUID.randomUUID();
        UUID posters = UUID.randomUUID();
        RecordInterests recordInterests = new RecordInterests(store);

        recordInterests.execute(ALICE, Set.of(sneakers, posters));
        recordInterests.execute(ALICE, Set.of(posters));

        assertThat(store.interests.get(ALICE)).containsExactly(posters);
    }
}
