package tn.sahticonnect.dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.NiveauRisque;
import tn.sahticonnect.dashboard.entity.PatientSuivi;
import tn.sahticonnect.dashboard.entity.StatutAlerte;
import tn.sahticonnect.dashboard.repository.AlerteRepository;
import tn.sahticonnect.dashboard.repository.PatientSuiviRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Patients suivis : recherche par filtre et calcul du niveau de risque. */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientSuiviRepository patientRepository;
    private final AlerteRepository alerteRepository;
    private final Clock horloge;

    /** Critères du filtre GraphQL FiltrePatients (tous facultatifs). */
    public record Filtre(String service, String pathologie, NiveauRisque niveauRisque) {
    }

    @Transactional(readOnly = true)
    public Optional<PatientSuivi> trouver(String id) {
        return patientRepository.findById(id);
    }

    public PatientSuivi exiger(String id) {
        return trouver(id).orElseThrow(() -> RessourceIntrouvableException.patient(id));
    }

    /**
     * Critères combinés par ET : service égal (insensible à la casse), pathologie qui contient le texte
     * (insensible à la casse), niveau de risque égal.
     */
    @Transactional(readOnly = true)
    public List<PatientSuivi> rechercher(Filtre filtre) {
        Specification<PatientSuivi> spec = (racine, requete, cb) -> cb.conjunction();
        if (filtre != null && filtre.service() != null && !filtre.service().isBlank()) {
            String service = filtre.service().trim().toLowerCase(Locale.ROOT);
            spec = spec.and((r, q, cb) -> cb.equal(cb.lower(r.get("service")), service));
        }
        if (filtre != null && filtre.pathologie() != null && !filtre.pathologie().isBlank()) {
            String motif = "%" + filtre.pathologie().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("pathologie")), motif));
        }
        if (filtre != null && filtre.niveauRisque() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("niveauRisque"), filtre.niveauRisque()));
        }
        return patientRepository.findAll(spec, Sort.by("id"));
    }

    /**
     * Recalcule et enregistre le niveau de risque, appelé à chaque alerte enregistrée et à chaque
     * changement de statut d'alerte :
     * <ul>
     *   <li>CRITIQUE : une alerte CRITIQUE non résolue dans les 24 h ;</li>
     *   <li>ELEVE : une alerte ELEVEE non résolue dans les 24 h ;</li>
     *   <li>MODERE : au moins une alerte (même résolue) dans les 7 jours ;</li>
     *   <li>FAIBLE sinon.</li>
     * </ul>
     */
    @Transactional
    public NiveauRisque recalculerRisque(String patientId) {
        PatientSuivi patient = exiger(patientId);
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        List<Alerte> recentes = alerteRepository.findByPatientIdAndHorodatageAfter(patientId,
                maintenant.minus(Duration.ofDays(7)));
        NiveauRisque niveau = calculer(recentes, maintenant);
        patient.setNiveauRisque(niveau);
        patientRepository.save(patient);
        return niveau;
    }

    /**
     * Recalcule le niveau de risque de tous les patients (tâche périodique) : sans nouvel événement,
     * une alerte qui dépasse 24 h (ou 7 jours) doit quand même faire baisser le niveau.
     *
     * @return nombre de patients dont le niveau a changé
     */
    @Transactional
    public int recalculerTous() {
        int changements = 0;
        for (PatientSuivi patient : patientRepository.findAll()) {
            NiveauRisque avant = patient.getNiveauRisque();
            if (recalculerRisque(patient.getId()) != avant) {
                changements++;
            }
        }
        return changements;
    }

    static NiveauRisque calculer(List<Alerte> alertesDesSeptJours, OffsetDateTime maintenant) {
        OffsetDateTime il24h = maintenant.minus(Duration.ofHours(24));
        List<Alerte> actives24h = alertesDesSeptJours.stream()
                .filter(a -> a.getStatut() != StatutAlerte.RESOLUE && a.getHorodatage().isAfter(il24h))
                .toList();
        if (actives24h.stream().anyMatch(a -> a.getNiveau() == NiveauAlerte.CRITIQUE)) {
            return NiveauRisque.CRITIQUE;
        }
        if (actives24h.stream().anyMatch(a -> a.getNiveau() == NiveauAlerte.ELEVEE)) {
            return NiveauRisque.ELEVE;
        }
        return alertesDesSeptJours.isEmpty() ? NiveauRisque.FAIBLE : NiveauRisque.MODERE;
    }
}
