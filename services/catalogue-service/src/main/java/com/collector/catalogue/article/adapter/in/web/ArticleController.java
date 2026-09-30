package com.collector.catalogue.article.adapter.in.web;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.collector.catalogue.article.application.ChangePrice;
import com.collector.catalogue.article.application.CreateDraft;
import com.collector.catalogue.article.application.GetArticle;
import com.collector.catalogue.article.application.ListArticles;
import com.collector.catalogue.article.application.ListMyArticles;
import com.collector.catalogue.article.application.RequestPhotoUpload;
import com.collector.catalogue.article.application.SubmitArticle;
import com.collector.catalogue.article.domain.ArticleStatus;
import com.collector.catalogue.shared.adapter.in.web.Members;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Le contrôleur ne contient aucune règle : il traduit (jeton vers Member, JSON vers commande,
 * résultat vers JSON) et délègue. L'autorisation métier (propriété) est dans les cas d'usage.
 */
@RestController
@RequestMapping("/api/v1")
class ArticleController {

    private final CreateDraft createDraft;
    private final RequestPhotoUpload requestPhotoUpload;
    private final SubmitArticle submitArticle;
    private final ChangePrice changePrice;
    private final GetArticle getArticle;
    private final ListArticles listArticles;
    private final ListMyArticles listMyArticles;
    private final MeterRegistry metrics;

    ArticleController(CreateDraft createDraft, RequestPhotoUpload requestPhotoUpload, SubmitArticle submitArticle,
                      ChangePrice changePrice, GetArticle getArticle, ListArticles listArticles,
                      ListMyArticles listMyArticles, MeterRegistry metrics) {
        this.createDraft = createDraft;
        this.requestPhotoUpload = requestPhotoUpload;
        this.submitArticle = submitArticle;
        this.changePrice = changePrice;
        this.getArticle = getArticle;
        this.listArticles = listArticles;
        this.listMyArticles = listMyArticles;
        this.metrics = metrics;
    }

    // Public : le catalogue se consulte sans compte.
    @GetMapping("/articles")
    ArticlePageResponse list(@RequestParam(name = "category", required = false) String category,
                             @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                             @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(ListArticles.MAX_PAGE_SIZE) int size) {
        return ArticlePageResponse.from(listArticles.execute(Optional.ofNullable(category), page, size));
    }

    // Jeton facultatif : un article non publié n'est visible que de son vendeur.
    @GetMapping("/articles/{id}")
    ArticleResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ArticleResponse.from(getArticle.execute(Members.optional(jwt), id));
    }

    @PostMapping("/articles")
    @PreAuthorize("hasRole('vendeur')")
    ResponseEntity<ArticleResponse> create(@Valid @RequestBody ArticleCreateRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        ArticleResponse created = ArticleResponse.from(createDraft.execute(Members.from(jwt), request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/articles/" + created.id())).body(created);
    }

    @PostMapping("/articles/{id}/photos")
    @PreAuthorize("hasRole('vendeur')")
    ResponseEntity<PhotoUploadResponse> requestPhoto(@PathVariable UUID id,
                                                     @Valid @RequestBody PhotoUploadRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        var upload = requestPhotoUpload.execute(Members.from(jwt), id, request.contentType(), request.sizeBytes());
        return ResponseEntity.status(201).body(PhotoUploadResponse.from(upload));
    }

    @PostMapping("/articles/{id}/submission")
    @PreAuthorize("hasRole('vendeur')")
    ArticleResponse submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        ArticleResponse submitted = ArticleResponse.from(submitArticle.execute(Members.from(jwt), id));
        metrics.counter("collector.articles.submitted").increment();
        return submitted;
    }

    @PatchMapping("/articles/{id}/price")
    @PreAuthorize("hasRole('vendeur')")
    ArticleResponse changePrice(@PathVariable UUID id, @Valid @RequestBody PriceChangeRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return ArticleResponse.from(changePrice.execute(Members.from(jwt), id, request.priceCents()));
    }

    @GetMapping("/me/articles")
    List<ArticleResponse> mine(@RequestParam(name = "status", required = false) ArticleStatus status,
                               @AuthenticationPrincipal Jwt jwt) {
        return listMyArticles.execute(Members.from(jwt), status).stream().map(ArticleResponse::from).toList();
    }
}
