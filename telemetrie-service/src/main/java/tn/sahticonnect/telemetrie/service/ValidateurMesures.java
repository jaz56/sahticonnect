package tn.sahticonnect.telemetrie.service;

import org.springframework.stereotype.Component;
import tn.sahticonnect.telemetrie.config.TelemetrieProperties;
import tn.sahticonnect.telemetrie.domaine.Constante;
import tn.sahticonnect.telemetrie.domaine.Valeurs;

import java.util.Optional;
import java.util.Set;

/**
 * Validation d'une mesure avant détection. Une mesure invalide n'est ni stockée ni analysée :
 * elle reçoit un accusé REJETEE avec le motif renvoyé ici.
 */
@Component
public class ValidateurMesures {

    private final Set<String> patients;

    public ValidateurMesures(TelemetrieProperties proprietes) {
        this.patients = proprietes.patients();
    }

    /**
     * @param constante constante correspondant au type reçu, ou null si le type est NON_SPECIFIE ou inconnu
     * @return le motif du rejet, ou vide si la mesure est valide
     */
    public Optional<String> motifDeRejet(String identifiantPatient, Constante constante, double valeur) {
        if (!estPatientSuivi(identifiantPatient)) {
            return Optional.of("Patient inconnu : " + (identifiantPatient == null || identifiantPatient.isBlank()
                    ? "identifiant absent" : identifiantPatient));
        }
        if (constante == null) {
            return Optional.of("Type de mesure non spécifié");
        }
        if (!constante.estPlausible(valeur)) {
            return Optional.of("Valeur hors plage physique pour " + constante.libelle() + " : "
                    + Valeurs.formater(valeur) + " (attendu : " + constante.plage() + ")");
        }
        return Optional.empty();
    }

    public boolean estPatientSuivi(String identifiantPatient) {
        return identifiantPatient != null && patients.contains(identifiantPatient);
    }
}
