package tn.sahticonnect.portail.entity;

import java.util.EnumSet;
import java.util.Set;

/** Statut d'une mission d'ambulance. */
public enum StatutMission {
    DEMANDEE, DEPECHEE, SUR_PLACE, TRANSPORT, TERMINEE, ANNULEE;

    /** Statuts d'une mission non terminée (le véhicule est occupé). */
    public static final Set<StatutMission> EN_COURS = EnumSet.of(DEMANDEE, DEPECHEE, SUR_PLACE, TRANSPORT);

    /** Étape suivante de la simulation : DEPECHEE → SUR_PLACE → TRANSPORT → TERMINEE. */
    public StatutMission suivant() {
        return switch (this) {
            case DEMANDEE -> DEPECHEE;
            case DEPECHEE -> SUR_PLACE;
            case SUR_PLACE -> TRANSPORT;
            case TRANSPORT, TERMINEE -> TERMINEE;
            case ANNULEE -> ANNULEE;
        };
    }
}
