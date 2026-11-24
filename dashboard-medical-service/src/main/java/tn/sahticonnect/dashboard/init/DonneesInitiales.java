package tn.sahticonnect.dashboard.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.EtatWorkflow;
import tn.sahticonnect.dashboard.entity.Intervention;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.NiveauRisque;
import tn.sahticonnect.dashboard.entity.PatientSuivi;
import tn.sahticonnect.dashboard.entity.StatutAlerte;
import tn.sahticonnect.dashboard.entity.StatutFacturation;
import tn.sahticonnect.dashboard.entity.Traitement;
import tn.sahticonnect.dashboard.entity.TypeConstante;
import tn.sahticonnect.dashboard.entity.TypeIntervention;
import tn.sahticonnect.dashboard.repository.AlerteRepository;
import tn.sahticonnect.dashboard.repository.InterventionRepository;
import tn.sahticonnect.dashboard.repository.NumerotationRepository;
import tn.sahticonnect.dashboard.repository.PatientSuiviRepository;
import tn.sahticonnect.dashboard.repository.TraitementRepository;
import tn.sahticonnect.dashboard.service.PatientService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Données de démonstration, relatives à l'heure courante :
 * <ul>
 *   <li>les 5 patients de CLAUDE.md (identités identiques aux services SOAP et REST) ;</li>
 *   <li>traitements cohérents avec les ordonnances de portail-patient-service ;</li>
 *   <li>quelques alertes et interventions pour que le dashboard ne soit pas vide.</li>
 * </ul>
 * Les alertes de démonstration utilisent des identifiants ALR-AAAA-9NNNNN, distincts de ceux que
 * produira telemetrie-service (ALR-AAAA-000001…). Le niveau de risque est ensuite calculé par la vraie règle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonneesInitiales implements CommandLineRunner {

    private final PatientSuiviRepository patientRepository;
    private final TraitementRepository traitementRepository;
    private final AlerteRepository alerteRepository;
    private final InterventionRepository interventionRepository;
    private final NumerotationRepository numerotation;
    private final PatientService patientService;
    private final Clock horloge;

    @Override
    @Transactional
    public void run(String... args) {
        if (patientRepository.count() > 0) {
            log.info("Données déjà présentes : initialisation ignorée");
            return;
        }
        OffsetDateTime maintenant = OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
        LocalDate aujourdhui = maintenant.toLocalDate();

        // ---------- Patients (médecin référent = médecin traitant du portail patient) ----------
        patientRepository.saveAll(List.of(
                patient("PAT-0001", "Ben Salah", "Ahmed", LocalDate.of(1958, 3, 14), "Cardiologie",
                        "Insuffisance cardiaque", "Dr Mohamed Chaabane"),
                patient("PAT-0002", "Trabelsi", "Leila", LocalDate.of(1965, 7, 22), "Cardiologie",
                        "Hypertension", "Dr Ines Haddad"),
                patient("PAT-0003", "Jaziri", "Karim", LocalDate.of(1952, 11, 5), "Pneumologie",
                        "BPCO", "Dr Sami Kallel"),
                patient("PAT-0004", "Mansour", "Sonia", LocalDate.of(1970, 2, 18), "Médecine interne",
                        "Diabète", "Dr Ines Haddad"),
                patient("PAT-0005", "Gharbi", "Hedi", LocalDate.of(1940, 9, 9), "Médecine interne",
                        "Personne âgée (chutes)", "Dr Ines Haddad")));

        // ---------- Traitements (mêmes médicaments et périodes que les ordonnances du REST) ----------
        traitementRepository.saveAll(List.of(
                traitement("TRT-001", "PAT-0001", "Furosémide 40 mg", "1 comprimé le matin",
                        aujourdhui.minusDays(20), aujourdhui.plusDays(70), "Dr Mohamed Chaabane"),
                traitement("TRT-002", "PAT-0001", "Bisoprolol 5 mg", "1 comprimé par jour",
                        aujourdhui.minusDays(20), aujourdhui.plusDays(70), "Dr Mohamed Chaabane"),
                traitement("TRT-003", "PAT-0001", "Ramipril 5 mg", "1 comprimé le soir",
                        aujourdhui.minusDays(200), aujourdhui.minusDays(110), "Dr Mohamed Chaabane"),
                traitement("TRT-004", "PAT-0002", "Amlodipine 5 mg", "1 comprimé par jour",
                        aujourdhui.minusDays(10), aujourdhui.plusDays(80), "Dr Ines Haddad"),
                traitement("TRT-005", "PAT-0003", "Tiotropium 18 µg", "1 inhalation par jour",
                        aujourdhui.minusDays(30), aujourdhui.plusDays(60), "Dr Sami Kallel"),
                traitement("TRT-006", "PAT-0003", "Salbutamol 100 µg", "2 bouffées si gêne respiratoire",
                        aujourdhui.minusDays(30), aujourdhui.plusDays(60), "Dr Sami Kallel"),
                traitement("TRT-007", "PAT-0003", "Prednisolone 20 mg", "2 comprimés le matin",
                        aujourdhui.minusDays(150), aujourdhui.minusDays(143), "Dr Sami Kallel"),
                traitement("TRT-008", "PAT-0004", "Metformine 850 mg", "1 comprimé matin et soir",
                        aujourdhui.minusDays(15), aujourdhui.plusDays(75), "Dr Ines Haddad"),
                traitement("TRT-009", "PAT-0005", "Paracétamol 1 g", "1 comprimé jusqu'à 3 fois par jour",
                        aujourdhui.minusDays(60), aujourdhui.minusDays(30), "Dr Ines Haddad"),
                traitement("TRT-010", "PAT-0005", "Vitamine D 100 000 UI", "1 ampoule",
                        aujourdhui.minusDays(60), aujourdhui.minusDays(59), "Dr Ines Haddad")));

        // ---------- Alertes de démonstration ----------
        int annee = maintenant.getYear();
        Alerte ahmed = alerteRepository.save(alerte(annee, 900001, "PAT-0001", NiveauAlerte.CRITIQUE,
                StatutAlerte.PRISE_EN_CHARGE, TypeConstante.FREQUENCE_CARDIAQUE, 142, "FC > 130",
                "Alerte CRITIQUE : fréquence cardiaque 142 bpm (FC > 130)", maintenant.minusHours(2)));
        Alerte leila = alerteRepository.save(alerte(annee, 900002, "PAT-0002", NiveauAlerte.ELEVEE,
                StatutAlerte.PRISE_EN_CHARGE, TypeConstante.TENSION_SYSTOLIQUE, 172, "Systolique ≥ 160",
                "Alerte ELEVEE : tension systolique 172 mmHg (Systolique ≥ 160)", maintenant.minusHours(5)));
        alerteRepository.save(alerte(annee, 900003, "PAT-0003", NiveauAlerte.ELEVEE,
                StatutAlerte.RESOLUE, TypeConstante.SPO2, 93, "SpO2 ≤ 94",
                "Alerte ELEVEE : saturation en oxygène 93 % (SpO2 ≤ 94)", maintenant.minus(Duration.ofDays(3))));
        alerteRepository.save(alerte(annee, 900004, "PAT-0005", NiveauAlerte.CRITIQUE,
                StatutAlerte.NOUVELLE, TypeConstante.CHUTE, 1, "Chute détectée",
                "Alerte CRITIQUE : chute détectée", maintenant.minusMinutes(30)));

        // ---------- Interventions de démonstration ----------
        Intervention ambulance = intervention(annee, "PAT-0001", TypeIntervention.AMBULANCE, ahmed.getId(),
                "Ambulance SAMU-TN-01 vers l'Hôpital Charles Nicolle", "AMB-" + annee + "-900001",
                EtatWorkflow.FACTUREE, StatutFacturation.FACTUREE, maintenant.minusHours(2).plusMinutes(1));
        // feuille certifiée présente aussi dans les données de dossier-cnam : V 1 + K 30 = 145,000 DT, APCI 100 %
        ambulance.setNumeroFeuilleSoins("FS-" + annee + "-900004");
        ambulance.setMontantTotal(new BigDecimal("145.000"));
        ambulance.setPartCNAM(new BigDecimal("145.000"));
        ambulance.setPartPatient(new BigDecimal("0.000"));
        ambulance.setDateMiseAJour(maintenant.minusHours(1));
        // PAT-0002 : vignette expirée → intervention quand même, facturation à régulariser
        Intervention rdv = intervention(annee, "PAT-0002", TypeIntervention.RDV_URGENCE, leila.getId(),
                "RDV d'urgence chez Dr Ines Haddad dans les 2 h", "RDV-" + annee + "-900001",
                EtatWorkflow.INTERVENTION_DECLENCHEE, StatutFacturation.A_REGULARISER, maintenant.minusHours(5).plusMinutes(1));
        interventionRepository.saveAll(List.of(ambulance, rdv));

        // ---------- Niveau de risque calculé par la règle (24 h / 7 jours) ----------
        patientRepository.findAll().forEach(p -> patientService.recalculerRisque(p.getId()));

        log.info("Données de démonstration chargées : 5 patients, {} traitements, {} alertes, {} interventions",
                traitementRepository.count(), alerteRepository.count(), interventionRepository.count());
    }

    private static PatientSuivi patient(String id, String nom, String prenom, LocalDate naissance, String service,
                                        String pathologie, String medecin) {
        return PatientSuivi.builder().id(id).nom(nom).prenom(prenom).dateNaissance(naissance).service(service)
                .pathologie(pathologie).niveauRisque(NiveauRisque.FAIBLE).medecinReferent(medecin).build();
    }

    private static Traitement traitement(String id, String patientId, String medicament, String posologie,
                                         LocalDate debut, LocalDate fin, String prescripteur) {
        return Traitement.builder().id(id).patientId(patientId).medicament(medicament).posologie(posologie)
                .dateDebut(debut).dateFin(fin).prescripteur(prescripteur).build();
    }

    private static Alerte alerte(int annee, int numero, String patientId, NiveauAlerte niveau, StatutAlerte statut,
                                 TypeConstante type, double valeur, String seuil, String message,
                                 OffsetDateTime horodatage) {
        Alerte a = new Alerte();
        a.setId("ALR-%d-%06d".formatted(annee, numero));
        a.setPatientId(patientId);
        a.setNiveau(niveau);
        a.setStatut(statut);
        a.setTypeConstante(type);
        a.setValeur(valeur);
        a.setSeuilFranchi(seuil);
        a.setMessage(message);
        a.setHorodatage(horodatage);
        return a;
    }

    private Intervention intervention(int annee, String patientId, TypeIntervention type, String alerteId,
                                      String description, String referenceExterne, EtatWorkflow etat,
                                      StatutFacturation facturation, OffsetDateTime creation) {
        Intervention i = new Intervention();
        i.setId("INT-%d-%06d".formatted(annee, numerotation.prochaineValeur(NumerotationRepository.SEQUENCE_INTERVENTIONS)));
        i.setPatientId(patientId);
        i.setType(type);
        i.setAlerteId(alerteId);
        i.setDescription(description);
        i.setReferenceExterne(referenceExterne);
        i.setEtat(etat);
        i.setStatutFacturation(facturation);
        i.setDateCreation(creation);
        return i;
    }
}
