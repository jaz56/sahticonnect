package tn.sahticonnect.dashboard.graphql;

import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import reactor.core.publisher.Mono;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.Traitement;
import tn.sahticonnect.dashboard.repository.AlerteRepository;
import tn.sahticonnect.dashboard.repository.TraitementRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Chargeurs groupés (DataLoader) pour PatientSuivi.alertes(statut) et PatientSuivi.traitements(actifs).
 * <p>
 * Ces deux champs ont un argument : {@code @BatchMapping} ne transmet pas les arguments du champ, on utilise
 * donc le mécanisme qu'il utilise lui-même, un DataLoader enregistré ici, appelé depuis un {@code @SchemaMapping}
 * qui filtre ensuite selon l'argument. Résultat identique : une seule requête SQL pour tous les patients
 * de la réponse, quel que soit leur nombre.
 */
@Configuration
public class ChargeursGroupes {

    public static final String ALERTES_PAR_PATIENT = "alertesParPatient";
    public static final String TRAITEMENTS_PAR_PATIENT = "traitementsParPatient";

    public ChargeursGroupes(BatchLoaderRegistry registre, AlerteRepository alertes, TraitementRepository traitements) {
        registre.<String, List<Alerte>>forName(ALERTES_PAR_PATIENT)
                .registerMappedBatchLoader((patientIds, environnement) -> Mono.fromSupplier(() ->
                        alertes.findByPatientIdInOrderByHorodatageDesc(patientIds).stream()
                                .collect(Collectors.groupingBy(Alerte::getPatientId))));
        registre.<String, List<Traitement>>forName(TRAITEMENTS_PAR_PATIENT)
                .registerMappedBatchLoader((patientIds, environnement) -> Mono.fromSupplier(() ->
                        traitements.findByPatientIdInOrderByDateDebutDesc(patientIds).stream()
                                .collect(Collectors.groupingBy(Traitement::getPatientId))));
    }
}
