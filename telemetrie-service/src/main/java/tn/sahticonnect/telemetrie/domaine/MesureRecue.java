package tn.sahticonnect.telemetrie.domaine;

import java.time.Instant;

/**
 * Mesure validée, indépendante des classes Protobuf.
 *
 * @param niveau niveau détecté par DetecteurSeuils (null tant que la mesure n'a pas été analysée) ;
 *               c'est la seule source de vérité des seuils, renvoyée aux clients dans Mesure.niveau
 */
public record MesureRecue(
        String identifiantPatient,
        String capteurId,
        Constante constante,
        double valeur,
        String unite,
        Instant horodatage,
        long sequence,
        Niveau niveau) {

    /** Copie de la mesure avec le niveau détecté. */
    public MesureRecue avecNiveau(Niveau niveauDetecte) {
        return new MesureRecue(identifiantPatient, capteurId, constante, valeur, unite, horodatage, sequence, niveauDetecte);
    }

    /** « fréquence cardiaque 142 bpm » */
    public String description() {
        String unite = this.unite == null || this.unite.isBlank() ? constante.unite() : this.unite;
        return constante == Constante.CHUTE
                ? (valeur == 1 ? "chute détectée" : "pas de chute")
                : constante.libelle() + " " + Valeurs.formater(valeur) + (unite.isEmpty() ? "" : " " + unite);
    }
}
