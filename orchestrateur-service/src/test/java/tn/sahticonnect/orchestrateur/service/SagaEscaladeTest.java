package tn.sahticonnect.orchestrateur.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.sahticonnect.orchestrateur.client.CnamClient;
import tn.sahticonnect.orchestrateur.client.CnamClient.Couverture;
import tn.sahticonnect.orchestrateur.client.CnamClient.FeuilleEmise;
import tn.sahticonnect.orchestrateur.client.DashboardClient;
import tn.sahticonnect.orchestrateur.client.ErreurMetierException;
import tn.sahticonnect.orchestrateur.client.PortailClient;
import tn.sahticonnect.orchestrateur.client.PortailClient.EtatRendezVous;
import tn.sahticonnect.orchestrateur.client.ReponseAbsenteException;
import tn.sahticonnect.orchestrateur.config.OrchestrateurProperties;
import tn.sahticonnect.orchestrateur.entity.Etape;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.NomEtape;
import tn.sahticonnect.orchestrateur.entity.StatutEtape;
import tn.sahticonnect.orchestrateur.entity.StatutFacturation;
import tn.sahticonnect.orchestrateur.entity.TypeEtape;
import tn.sahticonnect.orchestrateur.entity.TypeIntervention;
import tn.sahticonnect.orchestrateur.entity.TypeMesure;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.repository.WorkflowRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saga d'escalade : une alerte CRITIQUE compense le workflow ELEVEE récent du même patient (RDV d'urgence encore à
 * venir) avant de déclencher l'ambulance. Clients SOAP, REST et GraphQL simulés.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaEscaladeTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-24T10:00:00+01:00");
    private static final String RDV = "RDV-2026-000043";

    @Mock
    private WorkflowRepository repository;
    @Mock
    private DashboardClient dashboard;
    @Mock
    private CnamClient cnam;
    @Mock
    private PortailClient portail;
    @Mock
    private PublicationWorkflows publication;

    private WorkflowAlerteService service;
    private Workflow elevee;
    private Workflow critique;

    @BeforeEach
    void preparer() {
        OrchestrateurProperties proprietes = new OrchestrateurProperties("Africa/Tunis", Duration.ofSeconds(3),
                new OrchestrateurProperties.Telemetrie("localhost:9090"), new OrchestrateurProperties.Service("cnam"),
                new OrchestrateurProperties.Service("portail"), new OrchestrateurProperties.Service("dashboard"),
                new OrchestrateurProperties.Alertes(false, Duration.ofSeconds(1), Duration.ofSeconds(30)),
                new OrchestrateurProperties.Facturation("SahtiConnect – intervention à domicile", "Équipe SahtiConnect"),
                new OrchestrateurProperties.Escalade(Duration.ofMinutes(30)));
        Clock horloge = Clock.fixed(T0.toInstant(), TUNIS);
        service = new WorkflowAlerteService(repository, dashboard, cnam, portail, publication, proprietes, horloge,
                new SagaEscalade(repository, cnam, dashboard, portail, publication, proprietes, horloge));

        elevee = eleveeFacturee(T0.minusMinutes(10));
        critique = alerteCritique();
        when(repository.save(any(Workflow.class))).thenAnswer(appel -> appel.getArgument(0));
        when(repository.findById(elevee.getId())).thenAnswer(appel -> Optional.of(elevee));
        when(repository.findById(critique.getId())).thenAnswer(appel -> Optional.of(critique));
        when(repository.findByPatientIdAndNiveauAndDateCreationAfterOrderByDateCreationDesc(
                eq("PAT-0001"), eq(NiveauAlerte.ELEVEE), any())).thenAnswer(appel -> List.of(elevee));
        when(repository.findFirstByCompensePar(critique.getId())).thenAnswer(appel ->
                critique.getId().equals(elevee.getCompensePar()) ? Optional.of(elevee) : Optional.empty());

        // le RDV d'urgence du workflow ELEVEE est à venir (dans 40 min)
        when(portail.consulterRendezVous(RDV)).thenReturn(new EtatRendezVous(RDV, "CONFIRME", T0.plusMinutes(40)));
        // workflow CRITIQUE : tout fonctionne
        when(cnam.consulterVignette("PAT-0001")).thenReturn("VG-2026-000001");
        when(cnam.verifierDroits(anyString(), anyString(), any())).thenReturn(new Couverture(true, 100, "APCI 100 %"));
        when(portail.demanderAmbulance(anyString(), anyString(), any()))
                .thenReturn(new PortailClient.Mission("AMB-2026-000007", "SAMU-TN-01", 12, "Hôpital Charles Nicolle"));
        when(dashboard.creerIntervention(any())).thenReturn("INT-2026-000005");
        when(cnam.emettreFeuilleDeSoins(anyString(), anyString(), any(), anyString(), anyString(), anyList(), anyString()))
                .thenReturn(new FeuilleEmise("FS-2026-000003", new BigDecimal("145.000"), new BigDecimal("145.000"),
                        BigDecimal.ZERO, "CERTIFIEE"));
        // compensations
        when(cnam.annulerFeuilleDeSoins(anyString(), anyString())).thenAnswer(appel -> new FeuilleEmise(
                appel.getArgument(0), new BigDecimal("60.000"), new BigDecimal("60.000"), BigDecimal.ZERO, "ANNULEE"));
    }

    /** Workflow ELEVEE terminé : RDV d'urgence, fiche INT-2026-000004, feuille CS 1 (60,000 DT). */
    private static Workflow eleveeFacturee(OffsetDateTime creation) {
        Workflow wf = new Workflow();
        wf.setId("WF-2026-000020");
        wf.setAlerteId("ALR-2026-000019");
        wf.setPatientId("PAT-0001");
        wf.setNiveau(NiveauAlerte.ELEVEE);
        wf.setTypeMesure(TypeMesure.FREQUENCE_CARDIAQUE);
        wf.setValeur(118);
        wf.setSeuilFranchi("FC ≥ 110");
        wf.setDateCreation(creation);
        wf.setTentative(1);
        wf.setTypeIntervention(TypeIntervention.RDV_URGENCE);
        wf.setReferenceExterne(RDV);
        wf.setInterventionId("INT-2026-000004");
        wf.setNumeroFeuilleSoins("FS-2026-000002");
        wf.setMontantTotal(new BigDecimal("60.000"));
        wf.setStatutFacturation(StatutFacturation.FACTUREE);
        for (NomEtape nom : NomEtape.PRINCIPALES) {
            wf.ajouterEtape(new Etape(nom, StatutEtape.OK, creation, 20, 1, nom + " ok"));
        }
        wf.recalculerEtat(true);
        return wf;
    }

    private static Workflow alerteCritique() {
        Workflow wf = new Workflow();
        wf.setId("WF-2026-000021");
        wf.setAlerteId("ALR-2026-000020");
        wf.setPatientId("PAT-0001");
        wf.setNiveau(NiveauAlerte.CRITIQUE);
        wf.setTypeMesure(TypeMesure.FREQUENCE_CARDIAQUE);
        wf.setValeur(142);
        wf.setSeuilFranchi("FC > 130");
        wf.setMessageAlerte("Alerte CRITIQUE : FC 142 bpm");
        wf.setHorodatageAlerte(T0);
        wf.setDateCreation(T0);
        wf.setEtat(EtatWorkflow.ALERTE_RECUE);
        return wf;
    }

    private static List<String> compensations(Workflow wf) {
        return wf.getEtapes().stream().filter(Etape::estCompensation).map(e -> e.getNom() + ":" + e.getStatut()).toList();
    }

    private static List<NomEtape> etapesExecutees(Workflow wf) {
        return wf.getEtapes().stream().filter(e -> e.getType() == TypeEtape.EXECUTION).map(Etape::getNom).toList();
    }

    private static Etape etape(Workflow wf, NomEtape nom) {
        return wf.derniere(nom).orElseThrow();
    }

    // ===================== Tachycardie complète =====================

    @Test
    void tachycardieComplete_eleveeCompensee_critiqueFacturee_uneSeuleFactureActive() {
        Workflow resultat = service.executer(critique.getId());

        // workflow ELEVEE : compensé, dans l'ordre inverse de ses étapes
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.COMPENSE);
        assertThat(elevee.getStatutFacturation()).isEqualTo(StatutFacturation.ANNULEE);
        assertThat(elevee.getCompensePar()).isEqualTo("WF-2026-000021");
        assertThat(elevee.estEnCompensation()).isTrue();
        assertThat(compensations(elevee)).containsExactly("FACTUREE:OK", "FICHE_PUBLIEE:OK", "INTERVENTION_DECLENCHEE:OK");

        InOrder ordre = inOrder(cnam, dashboard, portail);
        ordre.verify(cnam).annulerFeuilleDeSoins("FS-2026-000002", "Escalade vers ambulance WF-2026-000021");
        ordre.verify(dashboard).mettreAJourIntervention(eq("INT-2026-000004"), argThat(m ->
                "COMPENSE".equals(m.etat()) && "ANNULEE".equals(m.statutFacturation())
                        && m.description().startsWith("Annulée : escalade vers ambulance")));
        ordre.verify(dashboard).resoudreAlerte("ALR-2026-000019", "Remplacée par l'alerte CRITIQUE ALR-2026-000020");
        ordre.verify(portail).annulerRendezVous(RDV);
        // … puis seulement l'ambulance
        ordre.verify(portail).demanderAmbulance(eq("PAT-0001"), eq("ALR-2026-000020"), any());

        // workflow CRITIQUE : étape ESCALADE avant l'ambulance, puis le parcours normal jusqu'à FACTUREE
        assertThat(resultat.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(etapesExecutees(resultat)).containsExactly(NomEtape.ALERTE_RECUE, NomEtape.DROITS_VERIFIES,
                NomEtape.ESCALADE, NomEtape.INTERVENTION_DECLENCHEE, NomEtape.FICHE_PUBLIEE, NomEtape.FACTUREE);
        assertThat(etape(resultat, NomEtape.ESCALADE).getMessage()).startsWith("WF-2026-000020 compensé");
        assertThat(resultat.getMontantTotal()).isEqualByComparingTo("145.000");

        // une seule facture active : la feuille CS 1 est annulée, seule celle de l'ambulance (145,000 DT) est émise
        verify(cnam, times(1)).annulerFeuilleDeSoins(anyString(), anyString());
        verify(cnam, times(1)).emettreFeuilleDeSoins(anyString(), anyString(), any(), anyString(), anyString(), anyList(), anyString());
        // la fiche compensée cite la fiche de l'ambulance, une fois celle-ci publiée
        verify(dashboard).mettreAJourIntervention(eq("INT-2026-000004"), argThat(m -> m.etat() == null
                && "Annulée : escalade vers ambulance INT-2026-000005".equals(m.description())));
    }

    // ===================== Pas d'escalade =====================

    @Test
    void pasDEscalade_alerteEleveeDePlusDe30Minutes() {
        elevee = eleveeFacturee(T0.minusMinutes(31));

        Workflow resultat = service.executer(critique.getId());

        assertThat(etapesExecutees(resultat)).doesNotContain(NomEtape.ESCALADE);
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        verify(cnam, never()).annulerFeuilleDeSoins(anyString(), anyString());
        verify(portail, never()).annulerRendezVous(anyString());
        assertThat(resultat.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
    }

    @Test
    void pasDEscalade_rdvDejaTermine() {
        when(portail.consulterRendezVous(RDV)).thenReturn(new EtatRendezVous(RDV, "TERMINE", T0.minusMinutes(5)));

        Workflow resultat = service.executer(critique.getId());

        assertThat(etapesExecutees(resultat)).doesNotContain(NomEtape.ESCALADE);
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(elevee.getCompensePar()).isNull();
        verify(cnam, never()).annulerFeuilleDeSoins(anyString(), anyString());
        verify(portail, never()).annulerRendezVous(anyString());
    }

    @Test
    void pasDEscalade_pourUneAlerteElevee() {
        critique.setNiveau(NiveauAlerte.ELEVEE);
        when(portail.demanderRendezVousUrgence(anyString(), anyString(), any()))
                .thenReturn(new PortailClient.RendezVous("RDV-2026-000044", "MED-001", T0.plusMinutes(30)));

        service.executer(critique.getId());

        verify(repository, never()).findByPatientIdAndNiveauAndDateCreationAfterOrderByDateCreationDesc(any(), any(), any());
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
    }

    // ===================== Compensation partielle =====================

    @Test
    void compensationPartielle_feuilleJamaisEmise_nEstPasAnnulee() {
        elevee.setNumeroFeuilleSoins(null);
        elevee.ajouterEtape(new Etape(NomEtape.FACTUREE, StatutEtape.ECHEC, T0.minusMinutes(9), 3000, 1, "CNAM indisponible"));

        service.executer(critique.getId());

        verify(cnam, never()).annulerFeuilleDeSoins(anyString(), anyString());
        assertThat(compensations(elevee)).containsExactly("FICHE_PUBLIEE:OK", "INTERVENTION_DECLENCHEE:OK");
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.COMPENSE);
    }

    // ===================== Échec de compensation, puis relance =====================

    @Test
    void echecDeCompensation_eleveeEnEchec_ambulanceQuandMeme_puisRelanceReprendLesCompensationsManquantes() {
        doThrow(new ReponseAbsenteException("dossier-cnam", "délai de 3 s dépassé", null))
                .when(cnam).annulerFeuilleDeSoins(anyString(), anyString());

        Workflow premierPassage = service.executer(critique.getId());

        // ELEVEE : arrêt à la première compensation en échec, rien d'autre n'est compensé
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.ECHEC);
        assertThat(elevee.getEtapeEnEchec()).isEqualTo(NomEtape.FACTUREE);
        assertThat(compensations(elevee)).containsExactly("FACTUREE:ECHEC");
        verify(dashboard, never()).resoudreAlerte(anyString(), anyString());
        verify(portail, never()).annulerRendezVous(anyString());
        // CRITIQUE : l'urgence vitale passe avant tout, l'ambulance part ; l'escalade incomplète est signalée
        verify(portail).demanderAmbulance(anyString(), anyString(), any());
        assertThat(etape(premierPassage, NomEtape.ESCALADE).getStatut()).isEqualTo(StatutEtape.ECHEC);
        assertThat(premierPassage.getEtat()).isEqualTo(EtatWorkflow.ECHEC);
        assertThat(premierPassage.getEtapeEnEchec()).isEqualTo(NomEtape.ESCALADE);

        // la CNAM revient ; POST /relancer sur le workflow ELEVEE → seules les compensations manquantes
        reset(cnam);
        when(cnam.annulerFeuilleDeSoins(anyString(), anyString())).thenReturn(new FeuilleEmise("FS-2026-000002",
                new BigDecimal("60.000"), new BigDecimal("60.000"), BigDecimal.ZERO, "ANNULEE"));
        Workflow relance = service.executer(elevee.getId());

        assertThat(relance.getEtat()).isEqualTo(EtatWorkflow.COMPENSE);
        assertThat(compensations(relance))
                .containsExactly("FACTUREE:ECHEC", "FACTUREE:OK", "FICHE_PUBLIEE:OK", "INTERVENTION_DECLENCHEE:OK");
        verify(dashboard, times(1)).resoudreAlerte(anyString(), anyString());
        verify(portail, times(1)).annulerRendezVous(RDV);

        // relance du workflow CRITIQUE : l'escalade constate la compensation (idempotente), sans nouvel appel
        Workflow critiqueRelance = service.executer(critique.getId());
        assertThat(critiqueRelance.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        verify(cnam, times(1)).annulerFeuilleDeSoins(anyString(), anyString());
        verify(portail, times(1)).annulerRendezVous(RDV);
        verify(portail, times(1)).demanderAmbulance(anyString(), anyString(), any()); // pas de 2e ambulance
    }

    // ===================== Idempotence =====================

    @Test
    void idempotence_rdvDejaAnnule_compensationReussie() {
        doThrow(new ErreurMetierException("portail-patient", "RDV_NON_ANNULABLE", "Le rendez-vous est déjà au statut ANNULE"))
                .when(portail).annulerRendezVous(RDV);
        when(portail.consulterRendezVous(RDV)).thenReturn(
                new EtatRendezVous(RDV, "CONFIRME", T0.plusMinutes(40)),   // règle d'escalade
                new EtatRendezVous(RDV, "ANNULE", T0.plusMinutes(40)));    // après le 409

        service.executer(critique.getId());

        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.COMPENSE);
        assertThat(elevee.derniereCompensation(NomEtape.INTERVENTION_DECLENCHEE).orElseThrow().getMessage())
                .isEqualTo("RDV " + RDV + " déjà annulé");
    }

    @Test
    void idempotence_compensationRejouee_aucunNouvelAppel() {
        service.executer(critique.getId());
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.COMPENSE);

        // une 2e exécution de la compensation (relance) ne rappelle aucun service
        new SagaEscalade(repository, cnam, dashboard, portail, publication, null, Clock.fixed(T0.toInstant(), TUNIS))
                .reprendre(elevee);

        verify(cnam, times(1)).annulerFeuilleDeSoins(anyString(), anyString());
        verify(dashboard, times(1)).resoudreAlerte(anyString(), anyString());
        verify(portail, times(1)).annulerRendezVous(RDV);
        assertThat(compensations(elevee)).hasSize(3);
    }

    @Test
    void rdvPasseATermineEntreLaVerificationEtLAnnulation_escaladeArreteeProprement() {
        doThrow(new ErreurMetierException("portail-patient", "RDV_NON_ANNULABLE", "Le rendez-vous est déjà au statut TERMINE"))
                .when(portail).annulerRendezVous(RDV);
        when(portail.consulterRendezVous(RDV)).thenReturn(
                new EtatRendezVous(RDV, "CONFIRME", T0.plusMinutes(40)),
                new EtatRendezVous(RDV, "TERMINE", T0.plusMinutes(40)));

        service.executer(critique.getId());

        Etape compensationRdv = elevee.derniereCompensation(NomEtape.INTERVENTION_DECLENCHEE).orElseThrow();
        assertThat(compensationRdv.getStatut()).isEqualTo(StatutEtape.IGNOREE);
        assertThat(compensationRdv.getMessage()).contains("le RDV a eu lieu, rien à compenser pour lui");
        assertThat(elevee.getEtat()).isEqualTo(EtatWorkflow.COMPENSE);
    }
}
