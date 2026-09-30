package com.collector.controle.pricecheck.domain;

/** Résultat du contrôle d'un prix : le verdict, le score d'anomalie et le motif éventuel. */
public record PriceCheck(Verdict verdict, double anomalyScore, String reason) {

    public enum Verdict { PUBLIE, EN_REVUE }

    /** Catégorie trop jeune pour juger : on publie sans conclure plutôt que de tout bloquer. */
    public static PriceCheck insufficientSample() {
        return new PriceCheck(Verdict.PUBLIE, 0.0, "insufficient_sample");
    }

    public boolean isOutlier() {
        return verdict == Verdict.EN_REVUE;
    }
}
