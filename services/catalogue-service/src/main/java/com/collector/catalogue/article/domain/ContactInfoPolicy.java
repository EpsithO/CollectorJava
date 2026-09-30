package com.collector.catalogue.article.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Interdiction d'échanger des coordonnées (e-mail, téléphone), y compris maquillées :
 * le paiement doit passer par la plateforme (sujet). Motifs validés sur des cas réels,
 * sans faux positif sur années, prix, tailles, références. Partagés avec le futur chat.
 */
public final class ContactInfoPolicy {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    private static final List<Pattern> PATTERNS = List.of(
            // e-mail, y compris « nom (at) domaine [dot] fr » et « arobase »
            Pattern.compile("[a-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\]|arobase)\\s*[a-z0-9-]+"
                    + "(?:\\s*(?:\\.|\\(dot\\)|\\[dot\\])\\s*[a-z0-9-]+)+", FLAGS),
            // téléphone français : 06 12 34 56 78, 0033 6…, +33 6…
            Pattern.compile("(?:\\+33|0033|\\b0)\\s*[1-9](?:[\\s.-]*\\d{2}){4}", FLAGS),
            // téléphone international
            Pattern.compile("\\+\\d{1,3}[\\s.-]?\\(?\\d{1,4}\\)?(?:[\\s.-]?\\d{2,4}){2,4}", FLAGS));

    private ContactInfoPolicy() {
    }

    public static boolean containsContactInfo(String text) {
        return text != null && PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    public static void requireNone(String text) {
        if (containsContactInfo(text)) {
            throw new ContactInfoForbiddenException();
        }
    }
}
