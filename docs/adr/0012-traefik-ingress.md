# ADR 0012 — Traefik comme point d'entrée (ingress-nginx retiré)

- Statut : accepté (30/09/2026)

## Contexte

Il faut un point d'entrée avec TLS, redirection HTTP vers HTTPS, limitation de débit, en-têtes de sécurité
(HSTS). Le projet Kubernetes a annoncé fin 2025 l'**arrêt de la maintenance d'ingress-nginx** (plus de
correctifs de sécurité depuis mars 2026, à vérifier) ; la règle du projet interdit d'adopter un logiciel en fin
de vie.

## Décision

**Traefik** (installé par Helm), Ingress standard **et** Middlewares : `rate-limit` (moyenne 50, rafale 100 par
IP), `security-headers` (HSTS un an, `nosniff`, `frameDeny`). Certificats par cert-manager (autorité locale en
démo). Trois hôtes : `api.collector.local`, `auth.collector.local`, `s3.collector.local`. Cible à terme : Gateway
API. Sur Minikube, **ne pas activer** l'addon `ingress` (c'est ingress-nginx).

## Alternatives écartées

| Option | Raison |
|---|---|
| **ingress-nginx** | Plus maintenu : pas de correctifs de sécurité |
| Spring Cloud Gateway | Pas de logique de routage métier en V1 ; un service de plus ; à reconsidérer pour un BFF |
| Istio / maillage | Trop lourd pour la V1 ; cité en remédiation (mTLS entre services) |
| Envoy Gateway / Gateway API d'emblée | Écosystème moins répandu côté documentation et démo ; migration possible plus tard |

## Conséquences

- Les Middlewares Traefik sont des CRD : `kubeconform` est lancé avec `-ignore-missing-schemas`.
- Le rate limiting par IP protège l'excès de charge ; le test de charge fait deux mesures (limite relevée pour
  mesurer la capacité, puis limite normale pour montrer les 429).
- Le rate limiting par **utilisateur** sur les écritures reste en remédiation (priorité bloquante).
- Les ports de management (8081) ne sont jamais routés par le point d'entrée.
