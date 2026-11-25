package tn.sahticonnect.orchestrateur.entity;

/** EXECUTION : étape normale ; COMPENSATION : annulation de l'étape de même nom (saga d'escalade). */
public enum TypeEtape {
    EXECUTION, COMPENSATION
}
