package tn.sahticonnect.dashboard.client;

import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.TypeConstante;

/**
 * Niveau d'une mesure pour l'affichage (champ ConstanteVitale.niveau) : DernieresMesures ne renvoie que
 * la valeur, le dashboard applique donc les mêmes seuils que telemetrie-service (règles de CLAUDE.md).
 */
final class NiveauMesure {

    private NiveauMesure() {
    }

    /** @return ELEVEE, CRITIQUE, ou null si la mesure est dans les normes */
    static NiveauAlerte de(TypeConstante type, double v) {
        return switch (type) {
            case FREQUENCE_CARDIAQUE -> v < 40 || v > 130 ? NiveauAlerte.CRITIQUE : v >= 110 ? NiveauAlerte.ELEVEE : null;
            case SPO2 -> v < 90 ? NiveauAlerte.CRITIQUE : v <= 94 ? NiveauAlerte.ELEVEE : null;
            case TENSION_SYSTOLIQUE -> v > 180 || v < 90 ? NiveauAlerte.CRITIQUE : v >= 160 ? NiveauAlerte.ELEVEE : null;
            case TENSION_DIASTOLIQUE -> v > 120 ? NiveauAlerte.CRITIQUE : v > 100 ? NiveauAlerte.ELEVEE : null;
            case TEMPERATURE -> v > 39.5 ? NiveauAlerte.CRITIQUE : v >= 38.5 ? NiveauAlerte.ELEVEE : null;
            case CHUTE -> v == 1 ? NiveauAlerte.CRITIQUE : null;
        };
    }
}
