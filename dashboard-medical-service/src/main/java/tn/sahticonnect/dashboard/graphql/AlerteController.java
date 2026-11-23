package tn.sahticonnect.dashboard.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.Intervention;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.repository.InterventionRepository;
import tn.sahticonnect.dashboard.service.AlerteService;
import tn.sahticonnect.dashboard.service.PublicateurEvenements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mutation enregistrerAlerte, subscription nouvelleAlerte et champ Alerte.intervention. */
@Controller
@RequiredArgsConstructor
public class AlerteController {

    private final AlerteService alerteService;
    private final InterventionRepository interventionRepository;
    private final PublicateurEvenements publicateur;

    /**
     * Idempotente : renvoyer la même alerte (même id ALR-…) ne crée ni doublon ni nouvel événement.
     * Si deux envois simultanés se croisent, le second échoue sur la clé primaire (transaction annulée) :
     * on relit alors l'alerte créée par le premier, dans une nouvelle transaction.
     */
    @MutationMapping
    public Alerte enregistrerAlerte(@Argument AlerteService.NouvelleAlerte input) {
        AlerteService.Resultat resultat;
        try {
            resultat = alerteService.enregistrer(input);
        } catch (DataIntegrityViolationException e) {
            resultat = new AlerteService.Resultat(alerteService.exiger(input.id()), false);
        }
        if (resultat.creee()) {
            publicateur.publier(resultat.alerte());
        }
        return resultat.alerte();
    }

    /** Filtres facultatifs ; niveauMinimum par défaut ELEVEE, donc toutes les alertes. */
    @SubscriptionMapping
    public Flux<Alerte> nouvelleAlerte(@Argument String patientId, @Argument NiveauAlerte niveauMinimum) {
        NiveauAlerte minimum = niveauMinimum != null ? niveauMinimum : NiveauAlerte.ELEVEE;
        return publicateur.alertes()
                .filter(a -> patientId == null || patientId.equals(a.getPatientId()))
                .filter(a -> a.getNiveau().estAuMoins(minimum));
    }

    /** Intervention liée à chaque alerte (la plus récente), en une seule requête pour toutes les alertes. */
    @BatchMapping(typeName = "Alerte")
    public Map<Alerte, Intervention> intervention(List<Alerte> alertes) {
        Map<String, Intervention> parAlerte = new HashMap<>();
        interventionRepository.findByAlerteIdInOrderByDateCreationDesc(alertes.stream().map(Alerte::getId).toList())
                .forEach(i -> parAlerte.putIfAbsent(i.getAlerteId(), i));
        Map<Alerte, Intervention> resultat = new HashMap<>();
        alertes.forEach(a -> resultat.put(a, parAlerte.get(a.getId())));
        return resultat;
    }
}
