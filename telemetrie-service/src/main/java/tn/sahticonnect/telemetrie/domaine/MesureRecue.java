package tn.sahticonnect.telemetrie.domaine;

import java.time.Instant;

/** Mesure validée, indépendante des classes Protobuf. */
public record MesureRecue(
        String identifiantPatient,
        String capteurId,
        Constante constante,
        double valeur,
        String unite,
        Instant horodatage,
        long sequence) {

    /** « fréquence cardiaque 142 bpm » */
    public String description() {
        String unite = this.unite == null || this.unite.isBlank() ? constante.unite() : this.unite;
        return constante == Constante.CHUTE
                ? (valeur == 1 ? "chute détectée" : "pas de chute")
                : constante.libelle() + " " + Valeurs.formater(valeur) + (unite.isEmpty() ? "" : " " + unite);
    }
}
