package tn.sahticonnect.dashboard.graphql;

import graphql.schema.DataFetchingEnvironment;
import lombok.RequiredArgsConstructor;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import tn.sahticonnect.dashboard.client.CnamClient;
import tn.sahticonnect.dashboard.client.ModelesDistants.ConstanteVitale;
import tn.sahticonnect.dashboard.client.ModelesDistants.Couverture;
import tn.sahticonnect.dashboard.client.ModelesDistants.HistoriqueCnam;
import tn.sahticonnect.dashboard.client.ModelesDistants.PageHistoriqueSoins;
import tn.sahticonnect.dashboard.client.TelemetrieClient;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.Intervention;
import tn.sahticonnect.dashboard.entity.PatientSuivi;
import tn.sahticonnect.dashboard.entity.StatutAlerte;
import tn.sahticonnect.dashboard.entity.Traitement;
import tn.sahticonnect.dashboard.entity.TypeConstante;
import tn.sahticonnect.dashboard.repository.InterventionRepository;
import tn.sahticonnect.dashboard.service.DonneesInvalidesException;
import tn.sahticonnect.dashboard.service.PatientService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Queries et champs de PatientSuivi.
 * <ul>
 *   <li>champs locaux : lus en base, les listes par chargement groupé (pas de N+1) ;</li>
 *   <li>champs distants (constantes, couvert, tauxRemboursement, historiqueSoins) : {@code @SchemaMapping},
 *       donc appelés UNIQUEMENT si le client les demande, en parallèle (thread virtuel) ; en cas de panne,
 *       le champ vaut null et une erreur SERVICE_INDISPONIBLE est ajoutée (voir GestionErreurs).</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
public class PatientController {

    static final int LIMITE_MAX = 100;

    private final PatientService patientService;
    private final InterventionRepository interventionRepository;
    private final TelemetrieClient telemetrie;
    private final CnamClient cnam;
    private final ExecutorService executeurAppelsDistants;
    private final Clock horloge;

    // ===================== Queries =====================

    /** Null (sans erreur) si le patient n'existe pas, comme l'indique le schéma. */
    @QueryMapping
    public PatientSuivi patient(@Argument String id) {
        return patientService.trouver(id).orElse(null);
    }

    @QueryMapping
    public List<PatientSuivi> patients(@Argument PatientService.Filtre filtre) {
        return patientService.rechercher(filtre);
    }

    /** Historique SOAP, paginé ici (page à partir de 0). */
    @QueryMapping
    public CompletableFuture<PageHistoriqueSoins> historiqueSoins(@Argument String patientId, @Argument int page,
                                                                  @Argument int taille, DataFetchingEnvironment env) {
        if (page < 0) {
            throw new DonneesInvalidesException("page doit être positive ou nulle (reçu : " + page + ")");
        }
        if (taille < 1 || taille > LIMITE_MAX) {
            throw new DonneesInvalidesException("taille doit être comprise entre 1 et " + LIMITE_MAX + " (reçu : " + taille + ")");
        }
        patientService.exiger(patientId);
        return historique(patientId, env).thenApply(h -> PageHistoriqueSoins.de(h.entrees(), page, taille));
    }

    // ===================== Champs locaux =====================

    @SchemaMapping(typeName = "PatientSuivi")
    public Integer age(PatientSuivi patient) {
        return patient.getDateNaissance() == null ? null
                : Period.between(patient.getDateNaissance(), LocalDate.now(horloge)).getYears();
    }

    @SchemaMapping(typeName = "PatientSuivi")
    public CompletableFuture<List<Alerte>> alertes(PatientSuivi patient, @Argument StatutAlerte statut,
                                                   DataLoader<String, List<Alerte>> alertesParPatient) {
        return alertesParPatient.load(patient.getId()).thenApply(alertes -> filtrer(alertes,
                a -> statut == null || a.getStatut() == statut));
    }

    @SchemaMapping(typeName = "PatientSuivi")
    public CompletableFuture<List<Traitement>> traitements(PatientSuivi patient, @Argument Boolean actifs,
                                                           DataLoader<String, List<Traitement>> traitementsParPatient) {
        LocalDate aujourdhui = LocalDate.now(horloge);
        return traitementsParPatient.load(patient.getId()).thenApply(traitements -> filtrer(traitements,
                t -> actifs == null || t.estActifLe(aujourdhui) == actifs));
    }

    /** Toutes les interventions des patients de la réponse en une seule requête. */
    @BatchMapping(typeName = "PatientSuivi")
    public Map<PatientSuivi, List<Intervention>> interventions(List<PatientSuivi> patients) {
        Map<String, List<Intervention>> parPatient = interventionRepository
                .findByPatientIdInOrderByDateCreationDesc(patients.stream().map(PatientSuivi::getId).toList())
                .stream().collect(Collectors.groupingBy(Intervention::getPatientId));
        return patients.stream().collect(Collectors.toMap(Function.identity(),
                p -> parPatient.getOrDefault(p.getId(), List.of())));
    }

    @SchemaMapping(typeName = "Traitement")
    public boolean actif(Traitement traitement) {
        return traitement.estActifLe(LocalDate.now(horloge));
    }

    // ===================== Champs distants =====================

    /** Source : gRPC DernieresMesures (telemetrie-service). */
    @SchemaMapping(typeName = "PatientSuivi")
    public CompletableFuture<List<ConstanteVitale>> constantes(PatientSuivi patient, @Argument TypeConstante type,
                                                               @Argument Integer limite) {
        int nombre = limite == null ? 20 : limite;
        if (nombre < 1 || nombre > LIMITE_MAX) {
            throw new DonneesInvalidesException("limite doit être comprise entre 1 et " + LIMITE_MAX + " (reçu : " + nombre + ")");
        }
        return CompletableFuture.supplyAsync(() -> telemetrie.dernieresMesures(patient.getId(), type, nombre).stream()
                .sorted(Comparator.comparing(ConstanteVitale::horodatage).reversed())
                .limit(nombre)
                .toList(), executeurAppelsDistants);
    }

    /** Source : SOAP consulterHistorique (vignette) puis verifierDroitsCNAM. */
    @SchemaMapping(typeName = "PatientSuivi")
    public CompletableFuture<Boolean> couvert(PatientSuivi patient, DataFetchingEnvironment env) {
        return couverture(patient.getId(), env).thenApply(Couverture::couvert);
    }

    /** Source : SOAP consulterHistorique (vignette) puis verifierDroitsCNAM. */
    @SchemaMapping(typeName = "PatientSuivi")
    public CompletableFuture<Integer> tauxRemboursement(PatientSuivi patient, DataFetchingEnvironment env) {
        return couverture(patient.getId(), env).thenApply(Couverture::tauxRemboursement);
    }

    /**
     * Appels SOAP partagés au sein d'une même requête GraphQL (contexte de la requête) : demander couvert,
     * tauxRemboursement et historiqueSoins ensemble ne fait qu'un consulterHistorique et un verifierDroitsCNAM.
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<HistoriqueCnam> historique(String patientId, DataFetchingEnvironment env) {
        return (CompletableFuture<HistoriqueCnam>) env.getGraphQlContext().computeIfAbsent("historique:" + patientId,
                cle -> CompletableFuture.supplyAsync(() -> cnam.consulterHistorique(patientId), executeurAppelsDistants));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Couverture> couverture(String patientId, DataFetchingEnvironment env) {
        return (CompletableFuture<Couverture>) env.getGraphQlContext().computeIfAbsent("couverture:" + patientId,
                cle -> historique(patientId, env).thenApplyAsync(
                        h -> cnam.verifierDroits(patientId, h.codeVignette()), executeurAppelsDistants));
    }

    private static <T> List<T> filtrer(List<T> elements, java.util.function.Predicate<T> condition) {
        return elements == null ? List.of() : elements.stream().filter(condition).toList();
    }
}
