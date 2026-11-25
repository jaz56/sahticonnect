package tn.sahticonnect.orchestrateur.entity;

/**
 * États du workflow (CLAUDE.md) : la dernière étape réussie, ou ECHEC ; COMPENSE quand le workflow a été annulé
 * par compensation lors d'une escalade.
 * ALERTE_RECUE → DROITS_VERIFIES → INTERVENTION_DECLENCHEE → FICHE_PUBLIEE → FACTUREE | ECHEC | COMPENSE
 */
public enum EtatWorkflow {
    ALERTE_RECUE, DROITS_VERIFIES, INTERVENTION_DECLENCHEE, FICHE_PUBLIEE, FACTUREE, ECHEC, COMPENSE;

    /** État atteint quand l'étape principale de même nom est réussie. */
    public static EtatWorkflow apres(NomEtape etape) {
        return valueOf(etape.name());
    }
}
