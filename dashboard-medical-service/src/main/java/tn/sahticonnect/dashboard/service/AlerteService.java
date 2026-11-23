package tn.sahticonnect.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.StatutAlerte;
import tn.sahticonnect.dashboard.entity.TypeConstante;
import tn.sahticonnect.dashboard.repository.AlerteRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

/** Enregistrement des alertes reçues par l'orchestrateur (flux gRPC SouscrireAlertes). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlerteService {

    private static final Pattern FORMAT_ID = Pattern.compile("ALR-\\d{4}-\\d{6}");

    private final AlerteRepository alerteRepository;
    private final PatientService patientService;
    private final Clock horloge;

    /** Données de la mutation enregistrerAlerte. */
    public record NouvelleAlerte(String id, String patientId, NiveauAlerte niveau, TypeConstante typeConstante,
                                 double valeur, String seuilFranchi, String message, OffsetDateTime horodatage) {
    }

    /** Résultat : l'alerte, et si elle vient d'être créée (une alerte rejouée n'est pas republiée). */
    public record Resultat(Alerte alerte, boolean creee) {
    }

    /**
     * IDEMPOTENT : l'orchestrateur peut réessayer après un échec réseau. Une alerte déjà enregistrée
     * (même identifiant ALR-…) est renvoyée telle quelle, sans doublon ni nouvel événement.
     */
    @Transactional
    public Resultat enregistrer(NouvelleAlerte donnees) {
        valider(donnees);
        Optional<Alerte> existante = alerteRepository.findById(donnees.id());
        if (existante.isPresent()) {
            log.info("Alerte {} déjà enregistrée : renvoyée sans doublon", donnees.id());
            return new Resultat(existante.get(), false);
        }
        patientService.exiger(donnees.patientId());

        Alerte alerte = new Alerte();
        alerte.setId(donnees.id());
        alerte.setPatientId(donnees.patientId());
        alerte.setNiveau(donnees.niveau());
        alerte.setStatut(StatutAlerte.NOUVELLE);
        alerte.setTypeConstante(donnees.typeConstante());
        alerte.setValeur(donnees.valeur());
        alerte.setSeuilFranchi(donnees.seuilFranchi());
        alerte.setMessage(donnees.message());
        alerte.setHorodatage(donnees.horodatage());
        // deux envois simultanés du même identifiant : le second échoue ici sur la clé primaire
        // (DataIntegrityViolationException, transaction annulée) ; l'appelant relit alors l'alerte
        // dans une nouvelle transaction (voir AlerteController)
        alerteRepository.saveAndFlush(alerte);
        patientService.recalculerRisque(donnees.patientId());
        return new Resultat(alerte, true);
    }

    /** Changement de statut d'une alerte, suivi du recalcul du niveau de risque du patient. */
    @Transactional
    public Alerte changerStatut(Alerte alerte, StatutAlerte statut) {
        alerte.setStatut(statut);
        alerteRepository.save(alerte);
        patientService.recalculerRisque(alerte.getPatientId());
        return alerte;
    }

    /**
     * Mutation resoudreAlerte : statut RESOLUE, commentaire et date de résolution, puis recalcul du niveau
     * de risque du patient. Une alerte déjà résolue est renvoyée telle quelle (l'appel peut être rejoué).
     */
    @Transactional
    public Alerte resoudre(String id, String commentaire) {
        Alerte alerte = exiger(id);
        if (alerte.getStatut() == StatutAlerte.RESOLUE) {
            return alerte;
        }
        alerte.setCommentaireResolution(commentaire);
        alerte.setDateResolution(OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS));
        return changerStatut(alerte, StatutAlerte.RESOLUE);
    }

    public Alerte exiger(String id) {
        return alerteRepository.findById(id).orElseThrow(() -> RessourceIntrouvableException.alerte(id));
    }

    private static void valider(NouvelleAlerte d) {
        if (d.id() == null || !FORMAT_ID.matcher(d.id()).matches()) {
            throw new DonneesInvalidesException("Identifiant d'alerte invalide : « " + d.id() + " » (format ALR-AAAA-NNNNNN)");
        }
        if (Double.isNaN(d.valeur()) || Double.isInfinite(d.valeur())) {
            throw new DonneesInvalidesException("Valeur de mesure invalide : " + d.valeur());
        }
        if (d.seuilFranchi() == null || d.seuilFranchi().isBlank()) {
            throw new DonneesInvalidesException("seuilFranchi est obligatoire");
        }
    }
}
