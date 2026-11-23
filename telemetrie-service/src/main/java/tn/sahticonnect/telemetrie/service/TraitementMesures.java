package tn.sahticonnect.telemetrie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.sahticonnect.telemetrie.domaine.AntiRebond;
import tn.sahticonnect.telemetrie.domaine.DetecteurSeuils;
import tn.sahticonnect.telemetrie.domaine.MesureRecue;
import tn.sahticonnect.telemetrie.domaine.Niveau;
import tn.sahticonnect.telemetrie.domaine.ResultatDetection;
import tn.sahticonnect.telemetrie.entity.Alerte;

import java.util.Optional;

/**
 * Traitement d'une mesure : validation → stockage → détection des seuils → anti-rebond → alerte.
 * Indépendant de gRPC : la couche grpc convertit les messages et diffuse les alertes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TraitementMesures {

    /**
     * Résultat pour l'accusé de réception ; alerte non nulle si une alerte vient d'être émise ;
     * mesure = mesure stockée, avec son niveau (null si rejetée).
     */
    public record Resultat(boolean acceptee, Niveau niveau, String message, Alerte alerte, MesureRecue mesure) {

        static Resultat rejet(String motif) {
            return new Resultat(false, null, motif, null, null);
        }
    }

    private final ValidateurMesures validateur;
    private final StockageMesures stockage;
    private final DetecteurSeuils detecteur;
    private final AntiRebond antiRebond;
    private final AlerteService alerteService;

    /** @param mesure mesure reçue ; sa constante est null si le type est NON_SPECIFIE ou inconnu */
    public Resultat traiter(MesureRecue mesure) {
        Optional<String> rejet = validateur.motifDeRejet(mesure.identifiantPatient(), mesure.constante(), mesure.valeur());
        if (rejet.isPresent()) {
            log.debug("Mesure #{} rejetée : {}", mesure.sequence(), rejet.get());
            return Resultat.rejet(rejet.get());
        }

        // la mesure est stockée avec son niveau : DernieresMesures le renvoie (seule source des seuils)
        ResultatDetection detection = detecteur.evaluer(mesure.constante(), mesure.valeur());
        MesureRecue stockee = mesure.avecNiveau(detection.niveau());
        stockage.ajouter(stockee);
        if (!detection.estAnormal()) {
            return new Resultat(true, Niveau.NORMAL, "Mesure acceptée", null, stockee);
        }

        if (!antiRebond.autoriser(mesure.identifiantPatient(), mesure.constante(), detection.niveau())) {
            return new Resultat(true, detection.niveau(), "Mesure acceptée : niveau " + detection.niveau()
                    + " (" + detection.seuilFranchi() + "), alerte déjà émise il y a moins de "
                    + antiRebond.fenetre().toSeconds() + " s", null, stockee);
        }

        Alerte alerte = alerteService.enregistrer(stockee, detection);
        log.info("{} {} pour {} : {}", alerte.getId(), alerte.getNiveau(), alerte.getIdentifiantPatient(),
                alerte.getMessage());
        return new Resultat(true, detection.niveau(), "Mesure acceptée : alerte " + detection.niveau() + " "
                + alerte.getId() + " émise (" + detection.seuilFranchi() + ")", alerte, stockee);
    }
}
