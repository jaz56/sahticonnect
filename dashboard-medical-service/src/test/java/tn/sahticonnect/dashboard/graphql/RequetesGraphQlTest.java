package tn.sahticonnect.dashboard.graphql;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tn.sahticonnect.dashboard.client.CnamClient;
import tn.sahticonnect.dashboard.client.ModelesDistants.ActeCote;
import tn.sahticonnect.dashboard.client.ModelesDistants.ConstanteVitale;
import tn.sahticonnect.dashboard.client.ModelesDistants.Couverture;
import tn.sahticonnect.dashboard.client.ModelesDistants.EntreeHistoriqueSoins;
import tn.sahticonnect.dashboard.client.ModelesDistants.HistoriqueCnam;
import tn.sahticonnect.dashboard.client.ServiceIndisponibleException;
import tn.sahticonnect.dashboard.client.TelemetrieClient;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.TypeConstante;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Queries GraphQL (lecture seule) sur les données de démonstration ; clients gRPC et SOAP simulés.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
class RequetesGraphQlTest {

    @Autowired
    private GraphQlTester graphQl;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private TelemetrieClient telemetrie;
    @MockitoBean
    private CnamClient cnam;

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-24T09:00:00+01:00");

    private static HistoriqueCnam historique() {
        return new HistoriqueCnam("VG-2026-000001", List.of(
                new EntreeHistoriqueSoins(LocalDate.of(2026, 9, 3), "CSB Bab Saadoun", "Contrôle tensionnel",
                        List.of(new ActeCote("C", 1, "Consultation")), null),
                new EntreeHistoriqueSoins(LocalDate.of(2026, 5, 18), "Hôpital Charles Nicolle", "Suivi",
                        List.of(new ActeCote("CS", 1, null), new ActeCote("B", 40, "Bilan BNP")), null),
                new EntreeHistoriqueSoins(LocalDate.of(2026, 2, 10), "Hôpital Charles Nicolle", "Décompensation",
                        List.of(new ActeCote("CS", 1, null)), "FS-2026-900001")));
    }

    @BeforeEach
    void simulerServicesDistants() {
        reset(telemetrie, cnam);
        when(telemetrie.dernieresMesures(anyString(), any(), anyInt())).thenReturn(List.of(
                new ConstanteVitale("m3", "PAT-0001", TypeConstante.FREQUENCE_CARDIAQUE, 142, "bpm", T0.plusSeconds(2),
                        NiveauAlerte.CRITIQUE),
                new ConstanteVitale("m2", "PAT-0001", TypeConstante.SPO2, 97, "%", T0.plusSeconds(1), null),
                new ConstanteVitale("m1", "PAT-0001", TypeConstante.FREQUENCE_CARDIAQUE, 75, "bpm", T0, null)));
        when(cnam.consulterHistorique(anyString())).thenReturn(historique());
        when(cnam.verifierDroits(anyString(), anyString())).thenReturn(new Couverture(true, 100));
    }

    private static boolean aLeCode(ResponseError erreur, String code) {
        return code.equals(erreur.getExtensions().get("code"));
    }

    // ===================== patient =====================

    @Test
    void fichePatientCompleteEnUneRequete() {
        graphQl.document("""
                        { patient(id: "PAT-0001") {
                            id nom prenom age service pathologie niveauRisque medecinReferent
                            couvert tauxRemboursement
                            constantes(limite: 2) { id type valeur unite niveau }
                            alertes { id niveau statut intervention { id type } }
                            traitements { medicament actif }
                            interventions { id etat statutFacturation patient { nom } alerte { id } }
                        } }""")
                .execute()
                .path("patient.nom").entity(String.class).isEqualTo("Ben Salah")
                .path("patient.service").entity(String.class).isEqualTo("Cardiologie")
                .path("patient.medecinReferent").entity(String.class).isEqualTo("Dr Mohamed Chaabane")
                .path("patient.niveauRisque").entity(String.class).isEqualTo("CRITIQUE")
                .path("patient.couvert").entity(Boolean.class).isEqualTo(true)
                .path("patient.tauxRemboursement").entity(Integer.class).isEqualTo(100)
                .path("patient.constantes").entityList(Map.class).hasSize(2)
                .path("patient.constantes[0].valeur").entity(Double.class).isEqualTo(142.0)
                .path("patient.constantes[0].niveau").entity(String.class).isEqualTo("CRITIQUE")
                .path("patient.alertes[0].id").entity(String.class).isEqualTo("ALR-2026-900001")
                .path("patient.alertes[0].intervention.type").entity(String.class).isEqualTo("AMBULANCE")
                .path("patient.traitements").entityList(Map.class).hasSize(2) // actifs par défaut
                .path("patient.interventions[0].patient.nom").entity(String.class).isEqualTo("Ben Salah")
                .path("patient.interventions[0].alerte.id").entity(String.class).isEqualTo("ALR-2026-900001");
        // couvert + tauxRemboursement : un seul appel SOAP de chaque opération
        verify(cnam, times(1)).consulterHistorique("PAT-0001");
        verify(cnam, times(1)).verifierDroits("PAT-0001", "VG-2026-000001");
        verify(telemetrie).dernieresMesures("PAT-0001", null, 2);
    }

    @Test
    void champsDistantsAppelesUniquementSiDemandes() {
        graphQl.document("{ patient(id: \"PAT-0003\") { nom alertes { id } traitements(actifs: false) { medicament } } }")
                .execute()
                .path("patient.nom").entity(String.class).isEqualTo("Jaziri")
                .path("patient.traitements[0].medicament").entity(String.class).isEqualTo("Prednisolone 20 mg");
        verifyNoInteractions(telemetrie, cnam);
    }

    @Test
    void constantesFiltreesParType() {
        graphQl.document("{ patient(id: \"PAT-0001\") { constantes(type: FREQUENCE_CARDIAQUE, limite: 5) { valeur } } }")
                .execute()
                .path("patient.constantes").entityList(Map.class).hasSize(3);
        verify(telemetrie).dernieresMesures("PAT-0001", TypeConstante.FREQUENCE_CARDIAQUE, 5);
    }

    @Test
    void patientInconnu_nullSansErreur() {
        graphQl.document("{ patient(id: \"PAT-0099\") { nom } }")
                .execute()
                .path("patient").valueIsNull();
    }

    @Test
    void limiteInvalide_badRequest() {
        graphQl.document("{ patient(id: \"PAT-0001\") { nom constantes(limite: 0) { valeur } } }")
                .execute()
                .errors().satisfy(erreurs -> assertThat(erreurs).singleElement()
                        .satisfies(e -> assertThat(aLeCode(e, "BAD_REQUEST")).isTrue()))
                .path("patient.nom").entity(String.class).isEqualTo("Ben Salah")
                .path("patient.constantes").valueIsNull();
    }

    // ===================== patients(filtre) =====================

    @Test
    void filtresCombinesParEt() {
        graphQl.document("{ patients { id } }").execute()
                .path("patients").entityList(Map.class).hasSize(5);
        graphQl.document("{ patients(filtre: { service: \"cardiologie\" }) { id } }").execute()
                .path("patients[*].id").entityList(String.class).containsExactly("PAT-0001", "PAT-0002");
        graphQl.document("{ patients(filtre: { pathologie: \"CARDI\" }) { id } }").execute()
                .path("patients[*].id").entityList(String.class).containsExactly("PAT-0001");
        graphQl.document("{ patients(filtre: { niveauRisque: CRITIQUE }) { id } }").execute()
                .path("patients[*].id").entityList(String.class).containsExactly("PAT-0001", "PAT-0005");
        graphQl.document("{ patients(filtre: { service: \"Médecine interne\", niveauRisque: CRITIQUE }) { id } }").execute()
                .path("patients[*].id").entityList(String.class).containsExactly("PAT-0005");
        graphQl.document("{ patients(filtre: { service: \"Pneumologie\", pathologie: \"diab\" }) { id } }").execute()
                .path("patients").entityList(Map.class).hasSize(0);
    }

    // ===================== historiqueSoins =====================

    @Test
    void historiquePagine() {
        graphQl.document("{ historiqueSoins(patientId: \"PAT-0001\", page: 0, taille: 2) { page taille totalElements totalPages elements { date } } }")
                .execute()
                .path("historiqueSoins.totalElements").entity(Integer.class).isEqualTo(3)
                .path("historiqueSoins.totalPages").entity(Integer.class).isEqualTo(2)
                .path("historiqueSoins.elements[*].date").entityList(String.class).containsExactly("2026-09-03", "2026-05-18");
        graphQl.document("{ historiqueSoins(patientId: \"PAT-0001\", page: 1, taille: 2) { elements { date actes { lettreCle coefficient } numeroFeuilleSoins } } }")
                .execute()
                .path("historiqueSoins.elements[0].numeroFeuilleSoins").entity(String.class).isEqualTo("FS-2026-900001")
                .path("historiqueSoins.elements[0].actes[0].lettreCle").entity(String.class).isEqualTo("CS");
    }

    @Test
    void historique_paginationInvalide_badRequest_patientInconnu_notFound() {
        graphQl.document("{ historiqueSoins(patientId: \"PAT-0001\", taille: 0) { totalElements } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "BAD_REQUEST")).isTrue()));
        graphQl.document("{ historiqueSoins(patientId: \"PAT-0001\", page: -1) { totalElements } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "BAD_REQUEST")).isTrue()));
        graphQl.document("{ historiqueSoins(patientId: \"PAT-0099\") { totalElements } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "NOT_FOUND")).isTrue()));
        verify(cnam, never()).consulterHistorique("PAT-0099");
    }

    // ===================== Réponses partielles =====================

    @Test
    void soapEnPanne_profilRenvoyeEtErreurServiceIndisponible() {
        when(cnam.consulterHistorique(anyString())).thenThrow(new ServiceIndisponibleException(
                ServiceIndisponibleException.DOSSIER_CNAM, "Connection refused", null));

        graphQl.document("""
                        { patient(id: "PAT-0001") { nom niveauRisque couvert tauxRemboursement alertes { id } }
                          historiqueSoins(patientId: "PAT-0001") { totalElements } }""")
                .execute()
                .errors().satisfy(erreurs -> {
                    assertThat(erreurs).hasSize(3).allSatisfy(e -> {
                        assertThat(aLeCode(e, "SERVICE_INDISPONIBLE")).isTrue();
                        assertThat(e.getExtensions()).containsEntry("service", "dossier-cnam");
                    });
                    assertThat(erreurs).extracting(ResponseError::getPath)
                            .containsExactlyInAnyOrder("historiqueSoins", "patient.couvert", "patient.tauxRemboursement");
                })
                .path("patient.nom").entity(String.class).isEqualTo("Ben Salah")
                .path("patient.niveauRisque").entity(String.class).isEqualTo("CRITIQUE")
                .path("patient.alertes[0].id").entity(String.class).isEqualTo("ALR-2026-900001")
                .path("patient.couvert").valueIsNull()
                .path("historiqueSoins").valueIsNull();
    }

    @Test
    void telemetrieEnPanne_constantesNullEtLeResteRenvoye() {
        when(telemetrie.dernieresMesures(anyString(), any(), anyInt())).thenThrow(new ServiceIndisponibleException(
                ServiceIndisponibleException.TELEMETRIE, "DEADLINE_EXCEEDED", null));

        graphQl.document("{ patients(filtre: { service: \"Cardiologie\" }) { id couvert constantes { valeur } } }")
                .execute()
                .errors().satisfy(erreurs -> assertThat(erreurs).hasSize(2).allSatisfy(e -> {
                    assertThat(aLeCode(e, "SERVICE_INDISPONIBLE")).isTrue();
                    assertThat(e.getExtensions()).containsEntry("service", "telemetrie");
                }))
                .path("patients[0].id").entity(String.class).isEqualTo("PAT-0001")
                .path("patients[0].couvert").entity(Boolean.class).isEqualTo(true)
                .path("patients[0].constantes").valueIsNull();
    }

    // ===================== N+1 =====================

    @Test
    void chargementGroupe_nombreDeRequetesSqlIndependantDuNombreDePatients() {
        String requete = """
                { patients%s {
                    id
                    alertes { id intervention { id patient { nom } alerte { id } } }
                    traitements(actifs: false) { id }
                    interventions { id patient { id } }
                } }""";
        // PAT-0001 a une alerte avec intervention : tous les niveaux de la requête sont exécutés
        long unPatient = requetesSql(requete.formatted("(filtre: { pathologie: \"cardiaque\" })"));
        long cinqPatients = requetesSql(requete.formatted(""));

        // Une requête par champ de liste et par profondeur, jamais une par patient :
        //   patients, alertes, traitements, interventions (des patients), intervention (des alertes),
        //   alerte (des interventions), patient (des interventions, aux 2 profondeurs où il apparaît)
        // = 8 requêtes, que la réponse contienne 1 patient ou 5. Sans chargement groupé : plusieurs
        // requêtes par patient et par alerte (problème N+1).
        assertThat(cinqPatients).isEqualTo(unPatient).isEqualTo(8);
    }

    private long requetesSql(String requete) {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        graphQl.document(requete).execute().path("patients").hasValue();
        return statistiques.getPrepareStatementCount();
    }
}
