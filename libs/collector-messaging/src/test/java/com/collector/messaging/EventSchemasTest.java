package com.collector.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventSchemasTest {

    private static final String PING = """
            {"event_id":"8f0c7a1e-0000-4000-8000-000000000001","type":"ping.created","version":1,
             "occurred_at":"2026-09-30T10:12:00.123Z",
             "data":{"id":1,"payload":"x","created_at":"2026-09-30T10:12:00Z"}}""";

    @Test
    void validMessageHasNoError() {
        assertThat(EventSchemas.validate(EventTypes.PING_CREATED, 1, PING)).isEmpty();
    }

    @Test
    void missingFieldIsReported() {
        String broken = PING.replace("\"payload\":\"x\",", "");
        assertThat(EventSchemas.validate(EventTypes.PING_CREATED, 1, broken)).isNotEmpty();
    }

    @Test
    void unknownFieldIsRejected() {
        String extra = PING.replace("\"id\":1,", "\"id\":1,\"surprise\":true,");
        assertThat(EventSchemas.validate(EventTypes.PING_CREATED, 1, extra)).isNotEmpty();
    }

    @Test
    void requireValidThrowsOnBrokenMessage() {
        assertThatThrownBy(() -> EventSchemas.requireValid(EventTypes.PING_CREATED, 1, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownSchemaIsRefused() {
        assertThatThrownBy(() -> EventSchemas.validate("nope", 1, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyEventTypeHasASchema() {
        for (String type : new String[] {EventTypes.ARTICLE_SUBMITTED, EventTypes.ARTICLE_CHECKED,
                EventTypes.FRAUD_ALERT, EventTypes.PRICE_CHANGED, EventTypes.INTERESTS_UPDATED,
                EventTypes.ARTICLE_REVIEWED, EventTypes.FOLLOW_CHANGED, EventTypes.PING_CREATED}) {
            assertThat(EventSchemas.validate(type, 1, "{}")).as(type).isNotEmpty();
        }
    }
}
