package tn.sahticonnect.orchestrateur.entity;

/**
 * États du workflow (CLAUDE.md) : la dernière étape réussie, ou ECHEC.
 * ALERTE_RECUE → DROITS_VERIFIES → INTERVENTION_DECLENCHEE → FICHE_PUBLIEE → FACTUREE | ECHEC
 */
public enum EtatWorkflow {
    ALERTE_RECUE, DROITS_VERIFIES, INTERVENTION_DECLENCHEE, FICHE_PUBLIEE, FACTUREE, ECHEC;

    /** État atteint quand l'étape de même nom est réussie. */
    public static EtatWorkflow apres(NomEtape etape) {
        return valueOf(etape.name());
    }
}
