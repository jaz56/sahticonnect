package tn.sahticonnect.cnam.entity;

/**
 * Statut d'une feuille de soins : CERTIFIEE si le patient est couvert, sinon EMISE (à régulariser) ;
 * ANNULEE après annulerFeuilleDeSoins (compensation d'une saga de l'orchestrateur).
 */
public enum StatutFeuille {
    EMISE,
    CERTIFIEE,
    ANNULEE
}
