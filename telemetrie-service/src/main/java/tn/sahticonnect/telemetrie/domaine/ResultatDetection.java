package tn.sahticonnect.telemetrie.domaine;

/**
 * Résultat de la détection : niveau atteint et seuil franchi lisible (« FC > 130 »).
 * seuilFranchi est null pour une mesure NORMAL.
 */
public record ResultatDetection(Niveau niveau, String seuilFranchi) {

    public static final ResultatDetection NORMAL = new ResultatDetection(Niveau.NORMAL, null);

    public static ResultatDetection elevee(String seuil) {
        return new ResultatDetection(Niveau.ELEVEE, seuil);
    }

    public static ResultatDetection critique(String seuil) {
        return new ResultatDetection(Niveau.CRITIQUE, seuil);
    }

    public boolean estAnormal() {
        return niveau != Niveau.NORMAL;
    }
}
