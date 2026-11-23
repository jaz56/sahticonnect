package tn.sahticonnect.dashboard.entity;

/** Niveau d'une alerte, du moins grave au plus grave (l'ordre sert au filtre niveauMinimum). */
public enum NiveauAlerte {
    ELEVEE,
    CRITIQUE;

    public boolean estAuMoins(NiveauAlerte autre) {
        return compareTo(autre) >= 0;
    }
}
