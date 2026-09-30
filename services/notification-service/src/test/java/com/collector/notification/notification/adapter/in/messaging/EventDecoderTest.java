package com.collector.notification.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

import com.collector.notification.notification.domain.FollowChange;
import com.collector.notification.notification.domain.PriceChange;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class EventDecoderTest {

    private static final UUID ID = UUID.fromString("8f0c7a1e-0000-4000-8000-0000000000a1");
    private static final String ENVELOPE = """
            {"event_id":"8f0c7a1e-0000-4000-8000-000000000001","type":"%s","version":1,
             "occurred_at":"2026-01-01T00:00:00Z","data":%s}""";

    private final EventDecoder decoder = new EventDecoder(
            JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build());

    private static Message message(String type, String data) {
        return new Message(ENVELOPE.formatted(type, data).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void decodesPriceChanged() {
        Message message = message("price.changed", """
                {"article_id":"%s","aggregate_version":3,"seller_id":"%s","category_id":"%s",
                 "title":"Air Jordan 1","old_price_cents":25000,"new_price_cents":24000,"currency":"EUR"}"""
                .formatted(ID, ID, ID));

        assertThat(decoder.priceChange(message))
                .isEqualTo(new PriceChange(ID, 3, "Air Jordan 1", 25_000, 24_000, "EUR"));
    }

    @Test
    void decodesFollowChanged() {
        Message message = message("follow.changed", """
                {"member_id":"%s","article_id":"%s","following":true,"changed_at":"2026-01-01T10:00:00Z"}"""
                .formatted(ID, ID));

        assertThat(decoder.followChange(message))
                .isEqualTo(new FollowChange(ID, ID, true, Instant.parse("2026-01-01T10:00:00Z")));
    }

    @Test
    void decodesInterestsUpdated() {
        Message message = message("interests.updated", """
                {"member_id":"%s","category_ids":["%s"]}""".formatted(ID, ID));

        var interests = decoder.interests(message);

        assertThat(interests.memberId()).isEqualTo(ID);
        assertThat(interests.categoryIds()).containsExactly(ID);
    }

    @Test
    void nonConformingOrUnreadableMessagesAreRejectedWithoutRequeue() {
        Message wrongType = message("follow.changed", """
                {"member_id":"%s","article_id":"%s","following":true,"changed_at":"2026-01-01T10:00:00Z"}"""
                .formatted(ID, ID));
        Message missingField = message("price.changed", "{\"article_id\":\"" + ID + "\"}");
        Message garbage = new Message("pas du json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> decoder.priceChange(wrongType)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> decoder.priceChange(missingField)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> decoder.followChange(garbage)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> decoder.interests(garbage)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
