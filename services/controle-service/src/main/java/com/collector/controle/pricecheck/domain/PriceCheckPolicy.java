package com.collector.controle.pricecheck.domain;

/**
 * La règle métier du contrôle automatique (CA-2 et CA-3) : un prix est anormal s'il s'écarte
 * de plus de 3 écarts-types de la médiane de sa catégorie. Java pur, sans aucun framework,
 * donc testable en une milliseconde et défendable devant le jury.
 */
public final class PriceCheckPolicy {

    /** Seuil en écarts-types : au-delà, l'article part en revue humaine. */
    static final double ANOMALY_THRESHOLD = 3.0;

    /** Sous cet effectif, l'écart-type n'est pas fiable (une médiane de 5 objets ne dit rien). */
    static final long MIN_SAMPLE_SIZE = 30;

    private PriceCheckPolicy() {
    }

    /**
     * score = |prix - médiane| / écart-type ; score &gt; 3 : EN_REVUE, sinon PUBLIE.
     *
     * <p>Choix assumés :
     * <ul>
     *   <li>valeur absolue : un prix très bas est aussi suspect (appât, contrefaçon) qu'un prix très haut ;</li>
     *   <li>la médiane plutôt que la moyenne : quelques pièces de collection exceptionnelles ne déplacent pas la référence ;</li>
     *   <li>strictement supérieur à 3 : exactement 3 écarts-types est publié ;</li>
     *   <li>échantillon trop petit ou écart-type nul : publié avec le motif {@code insufficient_sample},
     *       pour ne pas bloquer les catégories naissantes ni diviser par zéro.</li>
     * </ul>
     */
    public static PriceCheck evaluate(long priceCents, PriceStats stats) {
        if (stats.sampleSize() < MIN_SAMPLE_SIZE || stats.stddevCents() == null || stats.stddevCents() <= 0) {
            return PriceCheck.insufficientSample();
        }
        double score = Math.abs(priceCents - stats.medianCents()) / stats.stddevCents();
        return score > ANOMALY_THRESHOLD
                ? new PriceCheck(PriceCheck.Verdict.EN_REVUE, score, "price_outlier")
                : new PriceCheck(PriceCheck.Verdict.PUBLIE, score, null);
    }
}
