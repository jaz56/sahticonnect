package tn.sahticonnect.telemetrie.domaine;

/** Niveau d'une mesure, du moins grave au plus grave (l'ordre sert à détecter une aggravation). */
public enum Niveau {
    NORMAL,
    ELEVEE,
    CRITIQUE;

    public boolean estAuMoins(Niveau autre) {
        return compareTo(autre) >= 0;
    }
}
