package tn.sahticonnect.dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.EtatWorkflow;
import tn.sahticonnect.dashboard.entity.Intervention;
import tn.sahticonnect.dashboard.entity.StatutAlerte;
import tn.sahticonnect.dashboard.entity.StatutFacturation;
import tn.sahticonnect.dashboard.entity.TypeIntervention;
import tn.sahticonnect.dashboard.repository.InterventionRepository;
import tn.sahticonnect.dashboard.repository.NumerotationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** Création et suivi des interventions déclenchées par l'orchestrateur. */
@Service
@RequiredArgsConstructor
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final NumerotationRepository numerotation;
    private final PatientService patientService;
    private final AlerteService alerteService;
    private final Clock horloge;

    /** Données de la mutation creerIntervention. */
    public record NouvelleIntervention(String patientId, TypeIntervention type, String alerteId, String description,
                                       String referenceExterne, EtatWorkflow etat, StatutFacturation statutFacturation) {
    }

    /** Données de la mutation mettreAJourIntervention (null = inchangé). */
    public record Modification(EtatWorkflow etat, StatutFacturation statutFacturation, String referenceExterne,
                               String numeroFeuilleSoins, Double montantTotal, Double partCNAM, Double partPatient,
                               String description) {
    }

    /** Crée l'intervention (INT-AAAA-NNNNNN) ; l'alerte liée passe au statut PRISE_EN_CHARGE. */
    @Transactional
    public Intervention creer(NouvelleIntervention donnees) {
        patientService.exiger(donnees.patientId());
        Alerte alerte = null;
        if (donnees.alerteId() != null) {
            alerte = alerteService.exiger(donnees.alerteId());
            if (!alerte.getPatientId().equals(donnees.patientId())) {
                throw new DonneesInvalidesException("L'alerte " + alerte.getId() + " concerne le patient "
                        + alerte.getPatientId() + ", pas " + donnees.patientId());
            }
        }
        OffsetDateTime maintenant = maintenant();
        Intervention intervention = new Intervention();
        intervention.setId("INT-%d-%06d".formatted(maintenant.getYear(),
                numerotation.prochaineValeur(NumerotationRepository.SEQUENCE_INTERVENTIONS)));
        intervention.setPatientId(donnees.patientId());
        intervention.setType(donnees.type());
        intervention.setAlerteId(donnees.alerteId());
        intervention.setDescription(donnees.description());
        intervention.setReferenceExterne(donnees.referenceExterne());
        intervention.setEtat(donnees.etat() != null ? donnees.etat() : EtatWorkflow.ALERTE_RECUE);
        intervention.setStatutFacturation(donnees.statutFacturation() != null
                ? donnees.statutFacturation() : StatutFacturation.EN_ATTENTE);
        intervention.setDateCreation(maintenant);
        interventionRepository.save(intervention);

        if (alerte != null && alerte.getStatut() == StatutAlerte.NOUVELLE) {
            alerteService.changerStatut(alerte, StatutAlerte.PRISE_EN_CHARGE);
        }
        return intervention;
    }

    /** Met à jour les champs fournis et dateMiseAJour. */
    @Transactional
    public Intervention mettreAJour(String id, Modification m) {
        Intervention intervention = interventionRepository.findById(id)
                .orElseThrow(() -> RessourceIntrouvableException.intervention(id));
        if (m.etat() != null) {
            intervention.setEtat(m.etat());
        }
        if (m.statutFacturation() != null) {
            intervention.setStatutFacturation(m.statutFacturation());
        }
        if (m.referenceExterne() != null) {
            intervention.setReferenceExterne(m.referenceExterne());
        }
        if (m.numeroFeuilleSoins() != null) {
            intervention.setNumeroFeuilleSoins(m.numeroFeuilleSoins());
        }
        if (m.montantTotal() != null) {
            intervention.setMontantTotal(montant("montantTotal", m.montantTotal()));
        }
        if (m.partCNAM() != null) {
            intervention.setPartCNAM(montant("partCNAM", m.partCNAM()));
        }
        if (m.partPatient() != null) {
            intervention.setPartPatient(montant("partPatient", m.partPatient()));
        }
        if (m.description() != null) {
            intervention.setDescription(m.description());
        }
        intervention.setDateMiseAJour(maintenant());
        return interventionRepository.save(intervention);
    }

    /** Montant en DT : positif ou nul, 3 décimales. */
    private static BigDecimal montant(String champ, double valeur) {
        if (Double.isNaN(valeur) || valeur < 0) {
            throw new DonneesInvalidesException(champ + " doit être un montant positif ou nul (reçu : " + valeur + ")");
        }
        return BigDecimal.valueOf(valeur).setScale(3, RoundingMode.HALF_UP);
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
    }
}
