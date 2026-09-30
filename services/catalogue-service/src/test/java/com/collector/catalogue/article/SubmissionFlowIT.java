package com.collector.catalogue.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.collector.catalogue.AbstractIntegrationTest;
import com.collector.catalogue.article.application.ArticleView;
import com.collector.catalogue.article.application.CreateDraft;
import com.collector.catalogue.article.application.DraftCommand;
import com.collector.catalogue.article.application.RequestPhotoUpload;
import com.collector.catalogue.article.application.SubmitArticle;
import com.collector.catalogue.article.application.port.CategoryCatalog;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.shared.domain.Member;
import com.collector.catalogue.testsupport.FakeStorage;
import com.collector.messaging.EventSchemas;
import com.collector.messaging.EventTypes;
import com.collector.messaging.Topology;

/**
 * US-014 de bout en bout côté catalogue, avec PostgreSQL et RabbitMQ réels (stockage simulé) :
 * brouillon, photo, soumission, article.submitted publié, verdict consommé et appliqué.
 */
@Import(SubmissionFlowIT.FakeStorageConfig.class)
class SubmissionFlowIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        FakeStorage fakeStorage() {
            return new FakeStorage();
        }
    }

    @Autowired CreateDraft createDraft;
    @Autowired RequestPhotoUpload requestPhotoUpload;
    @Autowired SubmitArticle submitArticle;
    @Autowired CategoryCatalog categories;
    @Autowired FakeStorage storage;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    private final String subject = UUID.randomUUID().toString();
    private final Member seller = new Member(subject, "Vendeur IT", subject + "@test.local");

    @Test
    void submittedArticleIsPublishedThenPublieOnceTheVerdictArrives() {
        UUID sneakers = categories.idBySlug("sneakers").orElseThrow();
        ArticleView draft = createDraft.execute(seller, new DraftCommand("Air Jordan 1",
                "Paire neuve jamais portée", sneakers, 26_000, 500, Map.of()));
        UUID articleId = draft.article().id();
        var upload = requestPhotoUpload.execute(seller, articleId, "image/jpeg", 2_000);
        storage.upload("articles/" + articleId + "/" + upload.photoId(), "image/jpeg", 2_000);

        ArticleView submitted = submitArticle.execute(seller, articleId);

        assertThat(submitted.article().status()).isEqualTo(ArticleStatus.EN_CONTROLE);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message message = rabbit.receive(Topology.CONTROLE_ARTICLE_SUBMITTED);
            assertThat(message).isNotNull();
            assertThat(EventSchemas.validate(EventTypes.ARTICLE_SUBMITTED, 1, message.getBody())).isEmpty();
            assertThat(new String(message.getBody())).contains(articleId.toString());
        });

        // Le contrôle répond : article.checked PUBLIE, livré deux fois (au moins une fois).
        String checked = """
                {"event_id":"%s","type":"article.checked","version":1,"occurred_at":"2026-01-01T00:00:00Z",
                 "data":{"article_id":"%s","verdict":"PUBLIE","anomaly_score":0.4,"reason":null}}"""
                .formatted(UUID.randomUUID(), articleId);
        for (int i = 0; i < 2; i++) {
            rabbit.send(Topology.EXCHANGE, EventTypes.ARTICLE_CHECKED, MessageBuilder.withBody(checked.getBytes())
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT status FROM article WHERE id = :id").param("id", articleId)
                        .query(String.class).single()).isEqualTo("PUBLIE"));
    }

    @Test
    void unreadableVerdictGoesToTheDeadLetterQueue() {
        rabbit.send(Topology.EXCHANGE, EventTypes.ARTICLE_CHECKED,
                MessageBuilder.withBody("pas du json".getBytes()).build());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rabbit.receive(Topology.DEAD_LETTER_QUEUE)).isNotNull());
    }
}
