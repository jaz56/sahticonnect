package tn.sahticonnect.telemetrie.domaine;

/**
 * Constante vitale mesurée (mêmes noms que TypeMesure du contrat, sans la valeur NON_SPECIFIE).
 * Chaque constante connaît son unité, son libellé et sa plage physiquement possible : une valeur
 * hors plage est une erreur de capteur, rejetée avant toute détection.
 */
public enum Constante {
    FREQUENCE_CARDIAQUE("FC", "fréquence cardiaque", "bpm", 0, 300),
    SPO2("SpO2", "saturation en oxygène", "%", 0, 100),
    TENSION_SYSTOLIQUE("Systolique", "tension systolique", "mmHg", 0, 300),
    TENSION_DIASTOLIQUE("Diastolique", "tension diastolique", "mmHg", 0, 300),
    TEMPERATURE("Température", "température", "°C", 25, 45),
    CHUTE("Chute", "chute", "", 0, 1);

    private final String abreviation;
    private final String libelle;
    private final String unite;
    private final double minimum;
    private final double maximum;

    Constante(String abreviation, String libelle, String unite, double minimum, double maximum) {
        this.abreviation = abreviation;
        this.libelle = libelle;
        this.unite = unite;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /** Valeur physiquement possible ? (chute : uniquement 0 ou 1) */
    public boolean estPlausible(double valeur) {
        if (Double.isNaN(valeur) || Double.isInfinite(valeur)) {
            return false;
        }
        if (this == CHUTE) {
            return valeur == 0 || valeur == 1;
        }
        return valeur >= minimum && valeur <= maximum;
    }

    public String plage() {
        return this == CHUTE ? "0 ou 1" : Valeurs.formater(minimum) + " à " + Valeurs.formater(maximum) + " " + unite;
    }

    public String abreviation() {
        return abreviation;
    }

    public String libelle() {
        return libelle;
    }

    public String unite() {
        return unite;
    }
}
