package tn.sahticonnect.dashboard.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.Intervention;
import tn.sahticonnect.dashboard.entity.PatientSuivi;
import tn.sahticonnect.dashboard.repository.AlerteRepository;
import tn.sahticonnect.dashboard.repository.PatientSuiviRepository;
import tn.sahticonnect.dashboard.service.InterventionService;
import tn.sahticonnect.dashboard.service.PatientService;
import tn.sahticonnect.dashboard.service.PublicateurEvenements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Mutations et subscription des interventions, champs Intervention.patient et Intervention.alerte. */
@Controller
@RequiredArgsConstructor
public class InterventionController {

    private final InterventionService interventionService;
    private final PatientService patientService;
    private final PatientSuiviRepository patientRepository;
    private final AlerteRepository alerteRepository;
    private final PublicateurEvenements publicateur;

    /** Crée l'intervention, passe l'alerte liée en PRISE_EN_CHARGE et publie sur nouvelleIntervention. */
    @MutationMapping
    public Intervention creerIntervention(@Argument InterventionService.NouvelleIntervention input) {
        Intervention intervention = interventionService.creer(input);
        // publication après validation de la transaction : les abonnés voient une intervention enregistrée
        publicateur.publier(intervention, patientService.exiger(intervention.getPatientId()).getService());
        return intervention;
    }

    @MutationMapping
    public Intervention mettreAJourIntervention(@Argument String id, @Argument InterventionService.Modification input) {
        return interventionService.mettreAJour(id, input);
    }

    /** Filtres facultatifs par patient et par service hospitalier (insensible à la casse). */
    @SubscriptionMapping
    public Flux<Intervention> nouvelleIntervention(@Argument String patientId, @Argument String service) {
        return publicateur.interventions()
                .filter(e -> patientId == null || patientId.equals(e.intervention().getPatientId()))
                .filter(e -> service == null || service.equalsIgnoreCase(e.service()))
                .map(PublicateurEvenements.InterventionPubliee::intervention);
    }

    /** Patient de chaque intervention : une seule requête pour toutes les interventions de la réponse. */
    @BatchMapping(typeName = "Intervention")
    public Map<Intervention, PatientSuivi> patient(List<Intervention> interventions) {
        Map<String, PatientSuivi> patients = patientRepository
                .findAllById(interventions.stream().map(Intervention::getPatientId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PatientSuivi::getId, Function.identity()));
        Map<Intervention, PatientSuivi> resultat = new HashMap<>();
        interventions.forEach(i -> resultat.put(i, patients.get(i.getPatientId())));
        return resultat;
    }

    /** Alerte à l'origine de chaque intervention (null si créée manuellement). */
    @BatchMapping(typeName = "Intervention")
    public Map<Intervention, Alerte> alerte(List<Intervention> interventions) {
        List<String> ids = interventions.stream().map(Intervention::getAlerteId).filter(Objects::nonNull).distinct().toList();
        Map<String, Alerte> alertes = ids.isEmpty() ? Map.of() : alerteRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Alerte::getId, Function.identity()));
        Map<Intervention, Alerte> resultat = new HashMap<>();
        interventions.forEach(i -> resultat.put(i, i.getAlerteId() == null ? null : alertes.get(i.getAlerteId())));
        return resultat;
    }
}
