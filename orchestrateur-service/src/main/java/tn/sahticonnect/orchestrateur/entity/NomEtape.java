package tn.sahticonnect.orchestrateur.entity;

import java.util.List;

/**
 * Étapes du workflow, dans l'ordre d'exécution. ESCALADE n'existe que pour un workflow CRITIQUE qui a compensé un
 * workflow ELEVEE du même patient (saga) : elle s'exécute juste avant l'ambulance.
 */
public enum NomEtape {
    ALERTE_RECUE, DROITS_VERIFIES, ESCALADE, INTERVENTION_DECLENCHEE, FICHE_PUBLIEE, FACTUREE;

    /** Les 5 étapes de tout workflow (sans ESCALADE, facultative). */
    public static final List<NomEtape> PRINCIPALES =
            List.of(ALERTE_RECUE, DROITS_VERIFIES, INTERVENTION_DECLENCHEE, FICHE_PUBLIEE, FACTUREE);

    /** Étapes dont l'effet est compensé lors d'une escalade, dans l'ORDRE INVERSE de leur exécution. */
    public static final List<NomEtape> A_COMPENSER = List.of(FACTUREE, FICHE_PUBLIEE, INTERVENTION_DECLENCHEE);
}
