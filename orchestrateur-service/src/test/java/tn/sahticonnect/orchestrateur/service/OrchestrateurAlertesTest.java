package tn.sahticonnect.orchestrateur.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.TypeMesure;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.repository.NumerotationRepository;
import tn.sahticonnect.orchestrateur.repository.WorkflowRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Réception des alertes : idempotence, file par patient (ordre), parallélisme entre patients, relance. */
class OrchestrateurAlertesTest {

    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    private final WorkflowRepository repository = mock(WorkflowRepository.class);
    private final NumerotationRepository numerotation = mock(NumerotationRepository.class);
    private final WorkflowAlerteService workflowService = mock(WorkflowAlerteService.class);
    private final Map<String, Workflow> base = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final OrchestrateurAlertes orchestrateur = new OrchestrateurAlertes(repository, numerotation, workflowService,
            new ExecuteurParPatient(pool), Clock.system(ZoneId.of("Africa/Tunis")));

    OrchestrateurAlertesTest() {
        // base de données simulée, avec la contrainte d'unicité sur alerteId
        when(numerotation.prochaineValeur(anyString())).thenAnswer(a -> sequence.incrementAndGet());
        when(repository.findByAlerteId(anyString())).thenAnswer(a -> base.values().stream()
                .filter(w -> w.getAlerteId().equals(a.getArgument(0))).findFirst());
        when(repository.findById(anyString())).thenAnswer(a -> Optional.ofNullable(base.get((String) a.getArgument(0))));
        when(repository.saveAndFlush(any(Workflow.class))).thenAnswer(a -> {
            Workflow w = a.getArgument(0);
            synchronized (base) {
                if (base.values().stream().anyMatch(x -> x.getAlerteId().equals(w.getAlerteId()) && !x.getId().equals(w.getId()))) {
                    throw new DataIntegrityViolationException("uk_workflow_alerte");
                }
                base.put(w.getId(), w);
            }
            return w;
        });
        when(repository.save(any(Workflow.class))).thenAnswer(a -> a.getArgument(0));
    }

    @AfterEach
    void arreter() {
        pool.shutdownNow();
    }

    private static AlerteRecue alerte(String id, String patient) {
        return new AlerteRecue(id, patient, NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 142, "FC > 130",
                "Alerte CRITIQUE", OffsetDateTime.now());
    }

    @Test
    void memeAlerteDeuxFois_unSeulWorkflow() {
        OrchestrateurAlertes.Reception premiere = orchestrateur.recevoir(alerte("ALR-2026-000001", "PAT-0001"));
        OrchestrateurAlertes.Reception seconde = orchestrateur.recevoir(alerte("ALR-2026-000001", "PAT-0001"));

        assertThat(premiere.cree()).isTrue();
        assertThat(seconde.cree()).isFalse();
        assertThat(seconde.workflow().getId()).isEqualTo(premiere.workflow().getId()).matches("WF-\\d{4}-000001");
        assertThat(base).hasSize(1);
        verify(workflowService, timeout(2000).times(1)).executer(premiere.workflow().getId());
    }

    @Test
    void deuxAlertesDuMemePatient_traiteesDansLOrdre_uneALaFois() throws Exception {
        List<String> journal = new CopyOnWriteArrayList<>();
        CountDownLatch premiereBloquee = new CountDownLatch(1);
        when(workflowService.executer(anyString())).thenAnswer(a -> {
            String id = a.getArgument(0);
            journal.add("début " + id);
            if (id.endsWith("000001")) {
                premiereBloquee.await(5, TimeUnit.SECONDS); // la première alerte prend du temps
            }
            journal.add("fin " + id);
            return base.get(id);
        });

        orchestrateur.recevoir(alerte("ALR-2026-000001", "PAT-0001"));
        orchestrateur.recevoir(alerte("ALR-2026-000002", "PAT-0001"));
        Thread.sleep(200);
        assertThat(journal).containsExactly("début WF-" + OffsetDateTime.now().getYear() + "-000001"); // la 2e attend

        premiereBloquee.countDown();
        verify(workflowService, timeout(2000).times(2)).executer(anyString());
        Thread.sleep(50);
        String annee = "WF-" + OffsetDateTime.now().getYear();
        assertThat(journal).containsExactly("début " + annee + "-000001", "fin " + annee + "-000001",
                "début " + annee + "-000002", "fin " + annee + "-000002");
    }

    @Test
    void patientsDifferents_traitesEnParallele() throws Exception {
        CountDownLatch deuxDemarres = new CountDownLatch(2);
        CountDownLatch liberer = new CountDownLatch(1);
        when(workflowService.executer(anyString())).thenAnswer(a -> {
            deuxDemarres.countDown();
            liberer.await(5, TimeUnit.SECONDS);
            return null;
        });

        orchestrateur.recevoir(alerte("ALR-2026-000001", "PAT-0001"));
        orchestrateur.recevoir(alerte("ALR-2026-000002", "PAT-0003"));

        // les deux workflows tournent en même temps (sinon le second attendrait la libération du premier)
        assertThat(deuxDemarres.await(2, TimeUnit.SECONDS)).isTrue();
        liberer.countDown();
    }

    @Test
    void relance_seulementEnEchecEtPasEnCours() {
        Workflow wf = orchestrateur.recevoir(alerte("ALR-2026-000001", "PAT-0001")).workflow();
        verify(workflowService, timeout(2000)).executer(wf.getId());

        wf.setEnCours(false);
        wf.setEtat(EtatWorkflow.FACTUREE);
        assertThatThrownBy(() -> orchestrateur.relancer(wf.getId()))
                .isInstanceOf(WorkflowNonRelancableException.class).hasMessageContaining("FACTUREE");

        wf.setEtat(EtatWorkflow.ECHEC);
        wf.setEnCours(true);
        assertThatThrownBy(() -> orchestrateur.relancer(wf.getId()))
                .isInstanceOf(WorkflowNonRelancableException.class).hasMessageContaining("en cours");

        wf.setEnCours(false);
        orchestrateur.relancer(wf.getId());
        verify(workflowService, timeout(2000).times(2)).executer(wf.getId());

        assertThatThrownBy(() -> orchestrateur.relancer("WF-2026-999999")).isInstanceOf(WorkflowIntrouvableException.class);
    }
}
