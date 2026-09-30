package com.collector.controle.pricecheck.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

import com.collector.controle.pricecheck.application.CheckSubmittedArticle;
import com.collector.controle.pricecheck.domain.ArticleSubmitted;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** Décodage du message entrant : un message illisible va en lettres mortes, sans être rejoué. */
class ArticleSubmittedListenerTest {

    private static final UUID ARTICLE = UUID.fromString("8f0c7a1e-0000-4000-8000-0000000000a1");
    private static final UUID SELLER = UUID.fromString("8f0c7a1e-0000-4000-8000-0000000000b2");
    private static final UUID CATEGORY = UUID.fromString("8f0c7a1e-0000-4000-8000-0000000000c3");

    private static final String VALID = """
            {"event_id":"8f0c7a1e-0000-4000-8000-000000000001","type":"article.submitted","version":1,
             "occurred_at":"2026-01-01T00:00:00Z",
             "data":{"article_id":"%s","seller_id":"%s","category_id":"%s","price_cents":26000,
                     "currency":"EUR","photo_count":1}}""".formatted(ARTICLE, SELLER, CATEGORY);

    private final List<ArticleSubmitted> checked = new ArrayList<>();
    private final ArticleSubmittedListener listener = new ArticleSubmittedListener(
            new CheckSubmittedArticle(category -> java.util.Optional.empty(), new NoOpPublisher(checked)),
            JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build());

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void decodesAValidMessageIntoTheUsefulFields() {
        assertThat(listener.decode(VALID)).isEqualTo(new ArticleSubmitted(ARTICLE, SELLER, CATEGORY, 26_000));
    }

    @Test
    void aValidMessageIsHandedToTheUseCase() {
        listener.on(message(VALID));

        assertThat(checked).singleElement().extracting(ArticleSubmitted::articleId).isEqualTo(ARTICLE);
    }

    @Test
    void unreadableOrNonConformingMessagesAreRejectedWithoutRequeue() {
        for (String broken : new String[] {"pas du json", "{}", VALID.replace("\"price_cents\":26000,", ""),
                VALID.replace("article.submitted", "article.checked")}) {
            assertThatThrownBy(() -> listener.on(message(broken)))
                    .as(broken)
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        assertThat(checked).isEmpty();
    }

    private record NoOpPublisher(List<ArticleSubmitted> seen)
            implements com.collector.controle.pricecheck.application.port.VerdictPublisher {

        @Override
        public void publishVerdict(ArticleSubmitted article,
                                   com.collector.controle.pricecheck.domain.PriceCheck check) {
            seen.add(article);
        }

        @Override
        public void raiseFraudAlert(ArticleSubmitted article, com.collector.controle.pricecheck.domain.PriceCheck check,
                                    com.collector.controle.pricecheck.domain.PriceStats stats) {
            // non utilisé ici
        }
    }
}
