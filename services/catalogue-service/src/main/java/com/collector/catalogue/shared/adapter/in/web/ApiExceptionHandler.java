package com.collector.catalogue.shared.adapter.in.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.collector.catalogue.shared.domain.DomainException;

/** Erreurs au format RFC 9457 avec un code stable ; le détail technique reste dans les logs. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Exceptions métier du domaine, traduites ici en HTTP : le domaine ne connaît pas HTTP.
    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(statusOf(e), e.code(), e.getMessage());
    }

    // Sans ce handler, celui d'Exception transformerait les 403 de @PreAuthorize en 500.
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Not allowed");
    }

    // Deux modifications simultanées du même article : la seconde est refusée, pas écrasée.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail conflict(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "invalid_status", "The resource was modified concurrently");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Erreur inattendue", e);            // le détail reste dans les logs
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error");
    }

    // Corps invalide : champs en erreur, jamais la valeur saisie.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid_request", "Invalid request");
        problem.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "invalid_request", "Invalid request"));
    }

    // Tout le reste venu de Spring MVC (JSON illisible, champ inconnu, paramètre mal typé…)
    // est une requête invalide : même code stable, sans détail de désérialisation.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is4xxClientError()) {
            String code = statusCode.value() == 404 ? "not_found" : "invalid_request";
            ProblemDetail problem = problem(HttpStatus.valueOf(statusCode.value()), code, "Invalid request");
            return ResponseEntity.status(statusCode).headers(headers).body(problem);
        }
        log.error("Erreur Spring MVC", ex);
        return ResponseEntity.status(statusCode).headers(headers)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error"));
    }

    private static HttpStatus statusOf(DomainException e) {
        return switch (e.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RULE_VIOLATED -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
