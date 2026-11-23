package tn.sahticonnect.dashboard.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tn.sahticonnect.dashboard.client.CnamClient;
import tn.sahticonnect.dashboard.client.TelemetrieClient;
import tn.sahticonnect.dashboard.entity.StatutAlerte;
import tn.sahticonnect.dashboard.repository.AlerteRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mutations, erreurs et subscriptions. Ces tests modifient les données : le contexte Spring (et donc la base H2)
 * est recréé après la classe pour ne pas fausser les autres tests.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MutationsEtSubscriptionsTest {

    @Autowired
    private GraphQlTester graphQl;
    @Autowired
    private AlerteRepository alerteRepository;

    @MockitoBean
    private TelemetrieClient telemetrie;
    @MockitoBean
    private CnamClient cnam;

    private static final String ENREGISTRER_ALERTE = """
            mutation($input: EnregistrerAlerteInput!) {
              enregistrerAlerte(input: $input) { id patientId niveau statut seuilFranchi valeur }
            }""";

    private static Map<String, Object> alerte(String id, String patientId, String niveau, double valeur, String seuil) {
        return Map.of("id", id, "patientId", patientId, "niveau", niveau, "typeConstante", "SPO2",
                "valeur", valeur, "seuilFranchi", seuil, "message", "Alerte " + niveau + " de test",
                "horodatage", OffsetDateTime.now().minusMinutes(1).toString());
    }

    private GraphQlTester.Response enregistrer(Map<String, Object> input) {
        return graphQl.document(ENREGISTRER_ALERTE).variable("input", input).execute();
    }

    private String niveauRisque(String patientId) {
        return graphQl.document("query($id: ID!) { patient(id: $id) { niveauRisque } }").variable("id", patientId)
                .execute().path("patient.niveauRisque").entity(String.class).get();
    }

    private static boolean aLeCode(ResponseError erreur, String code) {
        return code.equals(erreur.getExtensions().get("code"));
    }

    // ===================== enregistrerAlerte =====================

    @Test
    void enregistrerAlerte_recalculeLeNiveauDeRisque() {
        assertThat(niveauRisque("PAT-0004")).isEqualTo("FAIBLE");

        enregistrer(alerte("ALR-2026-100001", "PAT-0004", "ELEVEE", 93, "SpO2 ≤ 94"))
                .path("enregistrerAlerte.statut").entity(String.class).isEqualTo("NOUVELLE")
                .path("enregistrerAlerte.niveau").entity(String.class).isEqualTo("ELEVEE");
        assertThat(niveauRisque("PAT-0004")).isEqualTo("ELEVE");

        enregistrer(alerte("ALR-2026-100002", "PAT-0004", "CRITIQUE", 86, "SpO2 < 90"));
        assertThat(niveauRisque("PAT-0004")).isEqualTo("CRITIQUE");
    }

    @Test
    void enregistrerAlerte_idempotente() {
        Map<String, Object> input = alerte("ALR-2026-100010", "PAT-0003", "ELEVEE", 92, "SpO2 ≤ 94");
        long avant = alerteRepository.count();

        enregistrer(input).path("enregistrerAlerte.id").entity(String.class).isEqualTo("ALR-2026-100010");
        // l'orchestrateur réessaie avec le même identifiant (et même des données différentes)
        enregistrer(Map.of("id", "ALR-2026-100010", "patientId", "PAT-0003", "niveau", "CRITIQUE",
                "typeConstante", "SPO2", "valeur", 80.0, "seuilFranchi", "SpO2 < 90",
                "horodatage", OffsetDateTime.now().toString()))
                .path("enregistrerAlerte.niveau").entity(String.class).isEqualTo("ELEVEE")
                .path("enregistrerAlerte.valeur").entity(Double.class).isEqualTo(92.0);

        assertThat(alerteRepository.count()).isEqualTo(avant + 1);
        assertThat(niveauRisque("PAT-0003")).isEqualTo("ELEVE");
    }

    @Test
    void enregistrerAlerte_erreurs() {
        enregistrer(alerte("ALR-2026-100020", "PAT-0099", "ELEVEE", 93, "SpO2 ≤ 94"))
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> {
                    assertThat(aLeCode(x, "NOT_FOUND")).isTrue();
                    assertThat(x.getMessage()).isEqualTo("Patient inconnu : PAT-0099");
                }));
        enregistrer(alerte("ALERTE-1", "PAT-0001", "ELEVEE", 93, "SpO2 ≤ 94"))
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "BAD_REQUEST")).isTrue()));
        enregistrer(alerte("ALR-2026-100021", "PAT-0001", "ELEVEE", 93, " "))
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "BAD_REQUEST")).isTrue()));
    }

    // ===================== resoudreAlerte =====================

    @Test
    void resoudreAlerte_statutResolueEtNiveauDeRisqueRecalcule() {
        // toutes les alertes de PAT-0002 non résolues (démo + éventuellement d'autres tests) sont résolues
        java.util.List<String> aResoudre = graphQl.document("{ patient(id: \"PAT-0002\") { alertes { id statut } } }")
                .execute().path("patient.alertes[?(@.statut != 'RESOLUE')].id").entityList(String.class).get();
        assertThat(aResoudre).contains("ALR-2026-900002");
        assertThat(niveauRisque("PAT-0002")).isIn("ELEVE", "CRITIQUE");

        for (String id : aResoudre) {
            graphQl.document("mutation($id: ID!) { resoudreAlerte(id: $id, commentaire: \"Tension contrôlée\") { id statut } }")
                    .variable("id", id)
                    .execute()
                    .path("resoudreAlerte.statut").entity(String.class).isEqualTo("RESOLUE");
        }

        // plus d'alerte non résolue dans les 24 h, mais une alerte dans les 7 jours
        assertThat(niveauRisque("PAT-0002")).isEqualTo("MODERE");
        assertThat(alerteRepository.findById("ALR-2026-900002")).get().satisfies(a -> {
            assertThat(a.getCommentaireResolution()).isEqualTo("Tension contrôlée");
            assertThat(a.getDateResolution()).isNotNull();
        });
        // rejouer la résolution : renvoyée telle quelle
        graphQl.document("mutation { resoudreAlerte(id: \"ALR-2026-900002\") { statut } }")
                .execute()
                .path("resoudreAlerte.statut").entity(String.class).isEqualTo("RESOLUE");
    }

    @Test
    void resoudreAlerte_idInconnu_notFound() {
        graphQl.document("mutation { resoudreAlerte(id: \"ALR-2026-999999\") { id } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> {
                    assertThat(aLeCode(x, "NOT_FOUND")).isTrue();
                    assertThat(x.getMessage()).isEqualTo("Alerte inconnue : ALR-2026-999999");
                }));
    }

    // ===================== creerIntervention / mettreAJourIntervention =====================

    @Test
    void creerIntervention_alertePriseEnCharge_valeursParDefaut() {
        enregistrer(alerte("ALR-2026-100030", "PAT-0002", "CRITIQUE", 85, "SpO2 < 90"));

        String id = graphQl.document("""
                        mutation { creerIntervention(input: { patientId: "PAT-0002", type: AMBULANCE,
                                   alerteId: "ALR-2026-100030", referenceExterne: "AMB-2026-000007" }) {
                            id etat statutFacturation referenceExterne dateCreation dateMiseAJour
                            patient { nom } alerte { id statut }
                        } }""")
                .execute()
                .path("creerIntervention.etat").entity(String.class).isEqualTo("ALERTE_RECUE")
                .path("creerIntervention.statutFacturation").entity(String.class).isEqualTo("EN_ATTENTE")
                .path("creerIntervention.patient.nom").entity(String.class).isEqualTo("Trabelsi")
                .path("creerIntervention.alerte.statut").entity(String.class).isEqualTo("PRISE_EN_CHARGE")
                .path("creerIntervention.dateMiseAJour").valueIsNull()
                .path("creerIntervention.id").entity(String.class).get();

        assertThat(id).matches("INT-\\d{4}-\\d{6}");
        assertThat(alerteRepository.findById("ALR-2026-100030")).get()
                .extracting(a -> a.getStatut()).isEqualTo(StatutAlerte.PRISE_EN_CHARGE);
    }

    @Test
    void creerIntervention_erreurs() {
        graphQl.document("mutation { creerIntervention(input: { patientId: \"PAT-0099\", type: AMBULANCE }) { id } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "NOT_FOUND")).isTrue()));
        graphQl.document("mutation { creerIntervention(input: { patientId: \"PAT-0001\", type: AMBULANCE, alerteId: \"ALR-2026-999999\" }) { id } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> {
                    assertThat(aLeCode(x, "NOT_FOUND")).isTrue();
                    assertThat(x.getMessage()).contains("ALR-2026-999999");
                }));
        // alerte d'un autre patient (ALR-2026-900004 concerne PAT-0005)
        graphQl.document("mutation { creerIntervention(input: { patientId: \"PAT-0001\", type: AMBULANCE, alerteId: \"ALR-2026-900004\" }) { id } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "BAD_REQUEST")).isTrue()));
    }

    @Test
    void mettreAJourIntervention_champsFournisEtDateMiseAJour() {
        String id = graphQl.document("mutation { creerIntervention(input: { patientId: \"PAT-0004\", type: TELECONSULTATION }) { id } }")
                .execute().path("creerIntervention.id").entity(String.class).get();

        graphQl.document("""
                        mutation($id: ID!) { mettreAJourIntervention(id: $id, input: {
                            etat: FACTUREE, statutFacturation: FACTUREE, numeroFeuilleSoins: "FS-2026-000012",
                            montantTotal: 45, partCNAM: 22.5, partPatient: 22.5 }) {
                            etat statutFacturation numeroFeuilleSoins montantTotal partCNAM partPatient type dateMiseAJour
                        } }""")
                .variable("id", id)
                .execute()
                .path("mettreAJourIntervention.etat").entity(String.class).isEqualTo("FACTUREE")
                .path("mettreAJourIntervention.numeroFeuilleSoins").entity(String.class).isEqualTo("FS-2026-000012")
                .path("mettreAJourIntervention.partCNAM").entity(Double.class).isEqualTo(22.5)
                .path("mettreAJourIntervention.type").entity(String.class).isEqualTo("TELECONSULTATION") // inchangé
                .path("mettreAJourIntervention.dateMiseAJour").hasValue();
    }

    @Test
    void mettreAJourIntervention_erreurs() {
        graphQl.document("mutation { mettreAJourIntervention(id: \"INT-2026-999999\", input: { etat: ECHEC }) { id } }")
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "NOT_FOUND")).isTrue()));
        String id = graphQl.document("mutation { creerIntervention(input: { patientId: \"PAT-0004\", type: VISITE_DOMICILE }) { id } }")
                .execute().path("creerIntervention.id").entity(String.class).get();
        graphQl.document("mutation($id: ID!) { mettreAJourIntervention(id: $id, input: { montantTotal: -5 }) { id } }")
                .variable("id", id)
                .execute()
                .errors().satisfy(e -> assertThat(e).singleElement().satisfies(x -> assertThat(aLeCode(x, "BAD_REQUEST")).isTrue()));
    }

    // ===================== Subscriptions =====================

    @Test
    void nouvelleAlerte_filtreParPatientEtNiveauMinimum() {
        Flux<String> alertes = graphQl.document(
                        "subscription { nouvelleAlerte(patientId: \"PAT-0005\", niveauMinimum: CRITIQUE) { id niveau } }")
                .executeSubscription()
                .toFlux("nouvelleAlerte.id", String.class);

        StepVerifier.create(alertes)
                .then(() -> {
                    enregistrer(alerte("ALR-2026-100040", "PAT-0005", "ELEVEE", 93, "SpO2 ≤ 94"));   // niveau trop bas
                    enregistrer(alerte("ALR-2026-100041", "PAT-0001", "CRITIQUE", 85, "SpO2 < 90")); // autre patient
                    enregistrer(alerte("ALR-2026-100042", "PAT-0005", "CRITIQUE", 85, "SpO2 < 90")); // reçue
                    enregistrer(alerte("ALR-2026-100042", "PAT-0005", "CRITIQUE", 85, "SpO2 < 90")); // rejouée : pas republiée
                })
                .expectNext("ALR-2026-100042")
                .expectNoEvent(Duration.ofMillis(300))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void nouvelleIntervention_filtreParService() {
        Flux<String> interventions = graphQl.document(
                        "subscription { nouvelleIntervention(service: \"pneumologie\") { patientId patient { service } } }")
                .executeSubscription()
                .toFlux("nouvelleIntervention.patientId", String.class);

        StepVerifier.create(interventions)
                .then(() -> {
                    graphQl.document("mutation { creerIntervention(input: { patientId: \"PAT-0001\", type: AMBULANCE }) { id } }")
                            .execute().path("creerIntervention.id").hasValue();
                    graphQl.document("mutation { creerIntervention(input: { patientId: \"PAT-0003\", type: RDV_URGENCE }) { id } }")
                            .execute().path("creerIntervention.id").hasValue();
                })
                .expectNext("PAT-0003")
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }
}
