package tn.sahticonnect.portail.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import tn.sahticonnect.portail.exception.AccesRefuseException;

import java.util.Optional;

/**
 * Contrôle d'appartenance. L'API Gateway vérifie le jeton OIDC et, pour un utilisateur PATIENT, ajoute l'en-tête
 * {@value #ENTETE} (claim patient_id du jeton ; un X-Patient-Id envoyé par le client est toujours supprimé).
 * <ul>
 *   <li>en-tête présent : le patient ne lit ou ne modifie que SES données, sinon 403 ACCES_REFUSE ;</li>
 *   <li>en-tête absent : appel interne (orchestrateur) ou médecin / administrateur → accès complet.</li>
 * </ul>
 * L'authentification est faite par la gateway (seul point d'entrée) : le service ne voit jamais le jeton.
 */
@Component
public class Appartenance {

    public static final String ENTETE = "X-Patient-Id";

    private final HttpServletRequest requete; // proxy Spring : la requête HTTP en cours

    public Appartenance(HttpServletRequest requete) {
        this.requete = requete;
    }

    /** Patient connecté (en-tête posé par la gateway), absent pour un appel interne, un médecin ou un admin. */
    public Optional<String> patientConnecte() {
        String valeur = requete.getHeader(ENTETE);
        return valeur == null || valeur.isBlank() ? Optional.empty() : Optional.of(valeur.trim());
    }

    /** Les données de {@code patientId} appartiennent-elles au patient connecté ? Sinon 403. */
    public void verifier(String patientId) {
        patientConnecte().ifPresent(connecte -> {
            if (!connecte.equals(patientId)) {
                throw new AccesRefuseException(connecte, patientId);
            }
        });
    }

    /** Filtre « patientId » d'une liste : imposé au patient connecté (absent → le sien, autre → 403). */
    public String filtrer(String patientIdDemande) {
        Optional<String> connecte = patientConnecte();
        if (connecte.isEmpty()) {
            return patientIdDemande;
        }
        if (patientIdDemande != null) {
            verifier(patientIdDemande);
        }
        return connecte.get();
    }
}
