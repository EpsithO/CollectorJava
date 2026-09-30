package com.collector.notification.shared.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.collector.notification.shared.domain.DomainException;

/** Erreurs au format RFC 9457 avec un code stable ; le détail technique reste dans les logs. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(statusOf(e), e.code(), e.getMessage());
    }

    // Sans ce handler, celui d'Exception transformerait les 403 de @PreAuthorize en 500.
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Not allowed");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Erreur inattendue", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error");
    }

    // Tout le reste venu de Spring MVC (identifiant mal formé, paramètre mal typé…) : même code stable.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is4xxClientError()) {
            String code = statusCode.value() == 404 ? "not_found" : "invalid_request";
            return ResponseEntity.status(statusCode).headers(headers)
                    .body(problem(HttpStatus.valueOf(statusCode.value()), code, "Invalid request"));
        }
        log.error("Erreur Spring MVC", ex);
        return ResponseEntity.status(statusCode).headers(headers)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error"));
    }

    private static HttpStatus statusOf(DomainException e) {
        return switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
