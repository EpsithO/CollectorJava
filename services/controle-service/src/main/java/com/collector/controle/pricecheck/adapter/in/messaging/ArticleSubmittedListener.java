package com.collector.controle.pricecheck.adapter.in.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.collector.controle.pricecheck.application.CheckSubmittedArticle;
import com.collector.controle.pricecheck.domain.ArticleSubmitted;
import com.collector.messaging.EventSchemas;
import com.collector.messaging.EventTypes;
import com.collector.messaging.Topology;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Décode et délègue : toute la décision est dans le cas d'usage. */
@Component
class ArticleSubmittedListener {

    private final CheckSubmittedArticle checkSubmittedArticle;
    private final JsonMapper json;

    ArticleSubmittedListener(CheckSubmittedArticle checkSubmittedArticle, JsonMapper json) {
        this.checkSubmittedArticle = checkSubmittedArticle;
        this.json = json;
    }

    // Acquittement automatique au retour de la méthode : on ne revient qu'après confirmation
    // des publications. Exception = remise en file (5 fois, puis lettres mortes). Message
    // illisible : inutile de le rejouer, lettres mortes directes.
    @RabbitListener(queues = Topology.CONTROLE_ARTICLE_SUBMITTED)
    void on(Message message) {
        ArticleSubmitted article;
        try {
            article = decode(new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException("article.submitted illisible ou non conforme", e);
        }
        checkSubmittedArticle.execute(article);
    }

    // Enveloppe + schéma article.submitted v1 : on ne fait confiance qu'à un message conforme.
    ArticleSubmitted decode(String body) {
        List<String> errors = EventSchemas.validate(EventTypes.ARTICLE_SUBMITTED, 1, body);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.toString());
        }
        JsonNode data = json.readTree(body).path("data");
        return new ArticleSubmitted(
                UUID.fromString(data.path("article_id").asString()),
                UUID.fromString(data.path("seller_id").asString()),
                UUID.fromString(data.path("category_id").asString()),
                data.path("price_cents").asLong());
    }
}
