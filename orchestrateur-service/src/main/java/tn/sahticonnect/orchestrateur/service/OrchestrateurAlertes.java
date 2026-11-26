package tn.sahticonnect.orchestrateur.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import tn.sahticonnect.orchestrateur.correlation.Correlation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.repository.NumerotationRepository;
import tn.sahticonnect.orchestrateur.repository.WorkflowRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Point d'entrée des alertes (gRPC ou déclenchement manuel) et des relances.
 * <ul>
 *   <li>idempotence : un seul workflow par alerteId (contrainte unique) ; une alerte reçue deux fois est ignorée ;</li>
 *   <li>exécution asynchrone dans la file du patient : un workflow à la fois par patient, dans l'ordre d'arrivée,
 *       des patients différents en parallèle.</li>
 * </ul>
 */
@Service
@Slf4j
public class OrchestrateurAlertes {

    /** Workflow et si l'appel vient de le créer (false : alerte déjà reçue, rien n'est relancé). */
    public record Reception(Workflow workflow, boolean cree) {
    }

    private final WorkflowRepository repository;
    private final NumerotationRepository numerotation;
    private final WorkflowAlerteService workflowService;
    private final ExecuteurParPatient executeur;
    private final Clock horloge;

    public OrchestrateurAlertes(WorkflowRepository repository, NumerotationRepository numerotation,
                                WorkflowAlerteService workflowService, ExecuteurParPatient executeur, Clock horloge) {
        this.repository = repository;
        this.numerotation = numerotation;
        this.workflowService = workflowService;
        this.executeur = executeur;
        this.horloge = horloge;
    }

    /** Alerte reçue : crée le workflow (si nouveau) et le met dans la file du patient. */
    public Reception recevoir(AlerteRecue alerte) {
        var existant = repository.findByAlerteId(alerte.alerteId());
        if (existant.isPresent()) {
            log.info("Alerte {} déjà reçue (workflow {}) : ignorée", alerte.alerteId(), existant.get().getId());
            return new Reception(existant.get(), false);
        }
        Workflow workflow;
        try {
            workflow = repository.saveAndFlush(nouveauWorkflow(alerte));
        } catch (DataIntegrityViolationException doublonSimultane) {
            // la même alerte reçue deux fois au même instant : la contrainte unique a tranché
            return new Reception(repository.findByAlerteId(alerte.alerteId()).orElseThrow(), false);
        }
        // alerte reçue par gRPC (aucune requête HTTP, donc aucun identifiant) : le workflow sert d'identifiant
        try (MDC.MDCCloseable c = Correlation.courant() == null ? MDC.putCloseable(Correlation.CLE_MDC, workflow.getId()) : null) {
            log.info("Alerte {} {} pour {} → workflow {}", alerte.niveau(), alerte.alerteId(), alerte.patientId(), workflow.getId());
            soumettre(workflow);
        }
        return new Reception(workflow, true);
    }

    /** Relance d'un workflow en ECHEC : seules les étapes qui n'ont pas réussi seront rejouées. */
    public Workflow relancer(String id) {
        Workflow workflow = consulter(id);
        if (workflow.isEnCours()) {
            throw new WorkflowNonRelancableException("Le workflow " + id + " est en cours d'exécution");
        }
        if (workflow.getEtat() != EtatWorkflow.ECHEC) {
            throw new WorkflowNonRelancableException("Le workflow " + id + " est à l'état " + workflow.getEtat()
                    + " (seul un workflow en ECHEC peut être relancé)");
        }
        workflow.setEnCours(true);
        workflow = repository.save(workflow);
        log.info("Relance du workflow {} (étape en cause : {})", id, workflow.getEtapeEnEchec());
        soumettre(workflow);
        return workflow;
    }

    public Workflow consulter(String id) {
        return repository.findById(id).orElseThrow(() -> new WorkflowIntrouvableException(id));
    }

    /** Le plus récent en premier, filtres facultatifs. */
    public List<Workflow> lister(String patientId, EtatWorkflow etat) {
        Specification<Workflow> spec = (r, q, cb) -> cb.conjunction();
        if (patientId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("patientId"), patientId));
        }
        if (etat != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("etat"), etat));
        }
        return repository.findAll(spec, Sort.by(Sort.Order.desc("dateCreation"), Sort.Order.desc("id")));
    }

    private CompletableFuture<Void> soumettre(Workflow workflow) {
        return executeur.soumettre(workflow.getPatientId(), () -> workflowService.executer(workflow.getId()));
    }

    private Workflow nouveauWorkflow(AlerteRecue alerte) {
        OffsetDateTime maintenant = OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.MILLIS);
        Workflow wf = new Workflow();
        wf.setId("WF-%d-%06d".formatted(maintenant.getYear(),
                numerotation.prochaineValeur(NumerotationRepository.SEQUENCE_WORKFLOWS)));
        wf.setAlerteId(alerte.alerteId());
        wf.setPatientId(alerte.patientId());
        wf.setNiveau(alerte.niveau());
        wf.setTypeMesure(alerte.typeMesure());
        wf.setValeur(alerte.valeur());
        wf.setSeuilFranchi(alerte.seuilFranchi());
        wf.setMessageAlerte(alerte.message());
        wf.setHorodatageAlerte(alerte.horodatage());
        wf.setEtat(EtatWorkflow.ALERTE_RECUE);
        wf.setEnCours(true);
        wf.setDateCreation(maintenant);
        wf.setDateMiseAJour(maintenant);
        return wf;
    }
}
