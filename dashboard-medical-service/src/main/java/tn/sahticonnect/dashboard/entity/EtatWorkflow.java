package tn.sahticonnect.dashboard.entity;

/** États du workflow de l'orchestrateur (CLAUDE.md) ; COMPENSE : intervention annulée lors d'une escalade (saga). */
public enum EtatWorkflow {
    ALERTE_RECUE, DROITS_VERIFIES, INTERVENTION_DECLENCHEE, FICHE_PUBLIEE, FACTUREE, ECHEC, COMPENSE
}
