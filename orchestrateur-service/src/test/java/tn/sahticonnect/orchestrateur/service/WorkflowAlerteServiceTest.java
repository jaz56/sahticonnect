package tn.sahticonnect.orchestrateur.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.sahticonnect.orchestrateur.client.CnamClient;
import tn.sahticonnect.orchestrateur.client.CnamClient.Acte;
import tn.sahticonnect.orchestrateur.client.CnamClient.Couverture;
import tn.sahticonnect.orchestrateur.client.CnamClient.FeuilleEmise;
import tn.sahticonnect.orchestrateur.client.ConnexionImpossibleException;
import tn.sahticonnect.orchestrateur.client.DashboardClient;
import tn.sahticonnect.orchestrateur.client.ErreurMetierException;
import tn.sahticonnect.orchestrateur.client.PortailClient;
import tn.sahticonnect.orchestrateur.config.OrchestrateurProperties;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.NomEtape;
import tn.sahticonnect.orchestrateur.entity.StatutEtape;
import tn.sahticonnect.orchestrateur.entity.StatutFacturation;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Un test par scénario du workflow, avec les 3 clients (SOAP, REST, GraphQL) simulés. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowAlerteServiceTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-24T09:48:00+01:00");

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
    private Workflow workflow;

    @BeforeEach
    void preparer() {
        OrchestrateurProperties proprietes = new OrchestrateurProperties("Africa/Tunis", Duration.ofSeconds(3),
                new OrchestrateurProperties.Telemetrie("localhost:9090"), new OrchestrateurProperties.Service("cnam"),
                new OrchestrateurProperties.Service("portail"), new OrchestrateurProperties.Service("dashboard"),
                new OrchestrateurProperties.Alertes(false, Duration.ofSeconds(1), Duration.ofSeconds(30)),
                new OrchestrateurProperties.Facturation("SahtiConnect – intervention à domicile", "Équipe SahtiConnect"));
        service = new WorkflowAlerteService(repository, dashboard, cnam, portail, publication, proprietes,
                Clock.fixed(T0.toInstant(), TUNIS));
        when(repository.save(any(Workflow.class))).thenAnswer(appel -> appel.getArgument(0));
        // par défaut : tout fonctionne
        when(cnam.consulterVignette(anyString())).thenAnswer(appel -> "VG-2026-00000" + ((String) appel.getArgument(0)).charAt(7));
        when(cnam.verifierDroits(anyString(), anyString(), any())).thenReturn(new Couverture(true, 80, "Filière publique 80 %"));
        when(portail.demanderAmbulance(anyString(), anyString(), any()))
                .thenReturn(new PortailClient.Mission("AMB-2026-000007", "SAMU-TN-01", 12, "Hôpital Charles Nicolle"));
        when(portail.demanderRendezVousUrgence(anyString(), anyString(), any()))
                .thenReturn(new PortailClient.RendezVous("RDV-2026-000043", "MED-002", T0.plusMinutes(27)));
        when(dashboard.creerIntervention(any())).thenReturn("INT-2026-000004");
        when(cnam.emettreFeuilleDeSoins(anyString(), anyString(), any(), anyString(), anyString(), anyList(), anyString()))
                .thenReturn(new FeuilleEmise("FS-2026-000003", new BigDecimal("145.000"), new BigDecimal("116.000"),
                        new BigDecimal("29.000"), "CERTIFIEE"));
    }

    private void alerte(String patientId, NiveauAlerte niveau, TypeMesure type, double valeur, String seuil) {
        workflow = new Workflow();
        workflow.setId("WF-2026-000012");
        workflow.setAlerteId("ALR-2026-000318");
        workflow.setPatientId(patientId);
        workflow.setNiveau(niveau);
        workflow.setTypeMesure(type);
        workflow.setValeur(valeur);
        workflow.setSeuilFranchi(seuil);
        workflow.setMessageAlerte("Alerte " + niveau + " : " + seuil);
        workflow.setHorodatageAlerte(T0);
        workflow.setEtat(EtatWorkflow.ALERTE_RECUE);
        workflow.setDateCreation(T0);
        when(repository.findById("WF-2026-000012")).thenReturn(Optional.of(workflow));
    }

    private Workflow executer() {
        return service.executer("WF-2026-000012");
    }

    private static List<StatutEtape> statuts(Workflow wf, int tentative) {
        return wf.getEtapes().stream().filter(e -> e.getTentative() == tentative).map(e -> e.getStatut()).toList();
    }

    private DashboardClient.NouvelleIntervention ficheCreee() {
        ArgumentCaptor<DashboardClient.NouvelleIntervention> fiche = ArgumentCaptor.forClass(DashboardClient.NouvelleIntervention.class);
        verify(dashboard).creerIntervention(fiche.capture());
        return fiche.getValue();
    }

    // ===================== Scénarios nominaux =====================

    @Test
    void pat0001_critique_couvert_ambulance_facturee() {
        alerte("PAT-0001", NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 142, "FC > 130");
        when(cnam.verifierDroits(eq("PAT-0001"), eq("VG-2026-000001"), any())).thenReturn(new Couverture(true, 100, "APCI 100 %"));
        when(cnam.emettreFeuilleDeSoins(anyString(), anyString(), any(), anyString(), anyString(), anyList(), anyString()))
                .thenReturn(new FeuilleEmise("FS-2026-000003", new BigDecimal("145.000"), new BigDecimal("145.000"),
                        BigDecimal.ZERO.setScale(3), "CERTIFIEE"));

        Workflow wf = executer();

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(wf.getEtapeEnEchec()).isNull();
        assertThat(wf.isEnCours()).isFalse();
        assertThat(wf.getEtapes()).extracting(e -> e.getNom()).containsExactly(NomEtape.values());
        assertThat(statuts(wf, 1)).containsOnly(StatutEtape.OK);
        assertThat(wf.getCouvert()).isTrue();
        assertThat(wf.getTauxRemboursement()).isEqualTo(100);
        assertThat(wf.getTypeIntervention()).isEqualTo(TypeIntervention.AMBULANCE);
        assertThat(wf.getReferenceExterne()).isEqualTo("AMB-2026-000007");
        assertThat(wf.getInterventionId()).isEqualTo("INT-2026-000004");
        assertThat(wf.getNumeroFeuilleSoins()).isEqualTo("FS-2026-000003");
        assertThat(wf.getStatutFacturation()).isEqualTo(StatutFacturation.FACTUREE);

        verify(cnam).verifierDroits("PAT-0001", "VG-2026-000001", Acte.de("V", 1));
        verify(portail, never()).demanderRendezVousUrgence(any(), any(), any());
        verify(cnam).emettreFeuilleDeSoins(eq("PAT-0001"), eq("VG-2026-000001"), eq(T0.toLocalDate()), anyString(), anyString(),
                eq(List.of(Acte.de("V", 1), Acte.de("K", 30))), eq("INT-2026-000004"));
        assertThat(ficheCreee()).satisfies(f -> {
            assertThat(f.type()).isEqualTo("AMBULANCE");
            assertThat(f.referenceExterne()).isEqualTo("AMB-2026-000007");
            assertThat(f.etat()).isEqualTo("INTERVENTION_DECLENCHEE");
            assertThat(f.statutFacturation()).isEqualTo("EN_ATTENTE");
            assertThat(f.description()).contains("APCI 100 %", "AMB-2026-000007");
        });
        ArgumentCaptor<DashboardClient.Modification> maj = ArgumentCaptor.forClass(DashboardClient.Modification.class);
        verify(dashboard).mettreAJourIntervention(eq("INT-2026-000004"), maj.capture());
        assertThat(maj.getValue().etat()).isEqualTo("FACTUREE");
        assertThat(maj.getValue().statutFacturation()).isEqualTo("FACTUREE");
        assertThat(maj.getValue().numeroFeuilleSoins()).isEqualTo("FS-2026-000003");
        verify(dashboard).enregistrerAlerte(any());
    }

    @Test
    void pat0002_nonCouvert_aRegulariser() {
        alerte("PAT-0002", NiveauAlerte.ELEVEE, TypeMesure.TENSION_SYSTOLIQUE, 172, "Systolique ≥ 160");
        when(cnam.verifierDroits(eq("PAT-0002"), anyString(), any()))
                .thenReturn(new Couverture(false, 0, "Vignette expirée le 30/06/2026"));
        when(cnam.emettreFeuilleDeSoins(anyString(), anyString(), any(), anyString(), anyString(), anyList(), anyString()))
                .thenReturn(new FeuilleEmise("FS-2026-000004", new BigDecimal("60.000"), BigDecimal.ZERO,
                        new BigDecimal("60.000"), "EMISE"));

        Workflow wf = executer();

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE); // toutes les étapes ont réussi
        assertThat(wf.getCouvert()).isFalse();
        assertThat(wf.getStatutFacturation()).isEqualTo(StatutFacturation.A_REGULARISER);
        assertThat(ficheCreee().statutFacturation()).isEqualTo("A_REGULARISER");
        assertThat(wf.derniere(NomEtape.DROITS_VERIFIES).orElseThrow().getMessage()).contains("Non couvert", "Vignette expirée");
        ArgumentCaptor<DashboardClient.Modification> maj = ArgumentCaptor.forClass(DashboardClient.Modification.class);
        verify(dashboard).mettreAJourIntervention(eq("INT-2026-000004"), maj.capture());
        assertThat(maj.getValue().statutFacturation()).isEqualTo("A_REGULARISER"); // feuille EMISE, pas CERTIFIEE
    }

    @Test
    void pat0003_elevee_rendezVousUrgence() {
        alerte("PAT-0003", NiveauAlerte.ELEVEE, TypeMesure.SPO2, 92, "SpO2 ≤ 94");

        Workflow wf = executer();

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(wf.getTypeIntervention()).isEqualTo(TypeIntervention.RDV_URGENCE);
        assertThat(wf.getReferenceExterne()).isEqualTo("RDV-2026-000043");
        verify(cnam).verifierDroits("PAT-0003", "VG-2026-000003", Acte.de("CS", 1));
        verify(portail).demanderRendezVousUrgence(eq("PAT-0003"), eq("ALR-2026-000318"), any());
        verify(portail, never()).demanderAmbulance(any(), any(), any());
        verify(cnam).emettreFeuilleDeSoins(anyString(), anyString(), any(), anyString(), anyString(),
                eq(List.of(Acte.de("CS", 1))), anyString());
        assertThat(ficheCreee().type()).isEqualTo("RDV_URGENCE");
    }

    // ===================== Pannes =====================

    @Test
    void soapEnPanne_onContinue_aRegulariser() {
        alerte("PAT-0001", NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 142, "FC > 130");
        org.mockito.Mockito.doThrow(new ConnexionImpossibleException("dossier-cnam", "Connection refused", null))
                .when(cnam).consulterVignette(anyString());

        Workflow wf = executer();

        // l'urgence vitale passe avant la facturation : l'ambulance est quand même envoyée
        verify(portail).demanderAmbulance(eq("PAT-0001"), anyString(), any());
        assertThat(wf.getCouvert()).isNull();
        assertThat(ficheCreee().statutFacturation()).isEqualTo("A_REGULARISER");
        assertThat(ficheCreee().description()).contains("couverture inconnue");
        // alerte OK, droits ECHEC (on continue), intervention OK, fiche OK, facturation ECHEC (SOAP toujours en panne)
        assertThat(statuts(wf, 1)).containsExactly(StatutEtape.OK, StatutEtape.ECHEC, StatutEtape.OK, StatutEtape.OK,
                StatutEtape.ECHEC);
        assertThat(wf.derniere(NomEtape.DROITS_VERIFIES).orElseThrow().getMessage()).contains("on continue");
        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.ECHEC);
        assertThat(wf.getEtapeEnEchec()).isEqualTo(NomEtape.DROITS_VERIFIES);
        verify(cnam, never()).emettreFeuilleDeSoins(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void restEnPanne_echecMaisFichePubliee() {
        alerte("PAT-0001", NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 142, "FC > 130");
        when(portail.demanderAmbulance(anyString(), anyString(), any()))
                .thenThrow(new ConnexionImpossibleException("portail-patient", "Connection refused", null));

        Workflow wf = executer();

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.ECHEC);
        assertThat(wf.getEtapeEnEchec()).isEqualTo(NomEtape.INTERVENTION_DECLENCHEE);
        // le médecin est prévenu malgré tout : fiche publiée à l'état ECHEC
        assertThat(ficheCreee()).satisfies(f -> {
            assertThat(f.etat()).isEqualTo("ECHEC");
            assertThat(f.description()).contains("NON déclenchée");
        });
        assertThat(statuts(wf, 1)).containsExactly(StatutEtape.OK, StatutEtape.OK, StatutEtape.ECHEC, StatutEtape.OK,
                StatutEtape.IGNOREE);
        verify(cnam, never()).emettreFeuilleDeSoins(any(), any(), any(), any(), any(), any(), any());
    }

    // ===================== Replis =====================

    @Test
    void aucunCreneauUrgence_teleconsultation() {
        alerte("PAT-0004", NiveauAlerte.ELEVEE, TypeMesure.SPO2, 92, "SpO2 ≤ 94");
        when(portail.demanderRendezVousUrgence(anyString(), anyString(), any()))
                .thenThrow(new ErreurMetierException("portail-patient", "AUCUN_CRENEAU_URGENCE", "Aucun créneau libre dans les 2 h pour MED-002"));

        Workflow wf = executer();

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(wf.getTypeIntervention()).isEqualTo(TypeIntervention.TELECONSULTATION);
        assertThat(wf.getReferenceExterne()).isNull();
        assertThat(wf.derniere(NomEtape.INTERVENTION_DECLENCHEE).orElseThrow().getMessage())
                .contains("téléconsultation", "rappel du patient par le médecin référent");
        assertThat(ficheCreee().type()).isEqualTo("TELECONSULTATION");
    }

    @Test
    void missionDejaEnCours_reutilisee() {
        alerte("PAT-0001", NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 145, "FC > 130");
        when(portail.demanderAmbulance(anyString(), anyString(), any())).thenThrow(new ErreurMetierException("portail-patient",
                "MISSION_DEJA_EN_COURS", "La mission AMB-2026-000005 est déjà en cours pour PAT-0001"));

        Workflow wf = executer();

        assertThat(wf.estReussie(NomEtape.INTERVENTION_DECLENCHEE)).isTrue();
        assertThat(wf.getReferenceExterne()).isEqualTo("AMB-2026-000005");
        assertThat(wf.derniere(NomEtape.INTERVENTION_DECLENCHEE).orElseThrow().getMessage()).contains("déjà en cours");
        assertThat(ficheCreee().referenceExterne()).isEqualTo("AMB-2026-000005");
    }

    @Test
    void aucunVehicule_repliSurRendezVousUrgence() {
        alerte("PAT-0001", NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 142, "FC > 130");
        when(portail.demanderAmbulance(anyString(), anyString(), any())).thenThrow(new ErreurMetierException("portail-patient",
                "AUCUN_VEHICULE_DISPONIBLE", "Les 5 ambulances sont déjà en mission"));

        Workflow wf = executer();

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(wf.getTypeIntervention()).isEqualTo(TypeIntervention.RDV_URGENCE);
        assertThat(wf.getReferenceExterne()).isEqualTo("RDV-2026-000043");
        assertThat(wf.derniere(NomEtape.INTERVENTION_DECLENCHEE).orElseThrow().getMessage())
                .contains("Repli", "aucune ambulance disponible");
        verify(portail).demanderRendezVousUrgence(eq("PAT-0001"), anyString(), any());
    }

    // ===================== Relance =====================

    @Test
    void relanceApresEchec_seulesLesEtapesManquantesSontRejouees() {
        alerte("PAT-0001", NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 142, "FC > 130");
        when(portail.demanderAmbulance(anyString(), anyString(), any()))
                .thenThrow(new ConnexionImpossibleException("portail-patient", "Connection refused", null))
                .thenReturn(new PortailClient.Mission("AMB-2026-000008", "SAMU-TN-02", 9, "Hôpital Charles Nicolle"));

        Workflow premiere = executer();
        assertThat(premiere.getEtat()).isEqualTo(EtatWorkflow.ECHEC);

        Workflow relance = executer();

        assertThat(relance.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(relance.getTentative()).isEqualTo(2);
        // tentative 2 : uniquement l'intervention (échouée) et la facturation (ignorée)
        assertThat(relance.getEtapes().stream().filter(e -> e.getTentative() == 2).map(e -> e.getNom()))
                .containsExactly(NomEtape.INTERVENTION_DECLENCHEE, NomEtape.FACTUREE);
        verify(dashboard, times(1)).enregistrerAlerte(any());
        verify(cnam, times(1)).verifierDroits(any(), any(), any());
        verify(dashboard, times(1)).creerIntervention(any());
        verify(portail, times(2)).demanderAmbulance(anyString(), anyString(), any());
        // la fiche déjà publiée est mise à jour avec l'intervention enfin déclenchée, puis facturée
        ArgumentCaptor<DashboardClient.Modification> maj = ArgumentCaptor.forClass(DashboardClient.Modification.class);
        verify(dashboard, times(2)).mettreAJourIntervention(eq("INT-2026-000004"), maj.capture());
        assertThat(maj.getAllValues()).extracting(DashboardClient.Modification::etat)
                .containsExactly("INTERVENTION_DECLENCHEE", "FACTUREE");
        assertThat(maj.getAllValues().getFirst().referenceExterne()).isEqualTo("AMB-2026-000008");
    }

    @Test
    void relance_feuilleDejaEmise_pasReemise() {
        alerte("PAT-0001", NiveauAlerte.CRITIQUE, TypeMesure.FREQUENCE_CARDIAQUE, 142, "FC > 130");
        // la feuille est émise mais la mise à jour du dashboard échoue
        org.mockito.Mockito.doThrow(new ConnexionImpossibleException("dashboard-medical", "Connection refused", null))
                .doNothing().when(dashboard).mettreAJourIntervention(anyString(), any());

        assertThat(executer().getEtapeEnEchec()).isEqualTo(NomEtape.FACTUREE);
        Workflow relance = executer();

        assertThat(relance.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        verify(cnam, times(1)).emettreFeuilleDeSoins(any(), any(), any(), any(), any(), any(), any()); // pas de doublon
    }
}
