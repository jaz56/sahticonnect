package tn.sahticonnect.orchestrateur.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.NomEtape;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.repository.WorkflowRepository;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Intégration : contexte Spring complet, vrais clients (JAXB, client REST généré, GraphQL, Resilience4j),
 * les 3 services HTTP simulés par WireMock (SOAP, REST, GraphQL).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrchestrateurIntegrationTest {

    private static final WireMockServer SERVICES = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AtomicInteger ALERTES = new AtomicInteger(500);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private WorkflowRepository repository;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeAll
    static void demarrer() {
        SERVICES.start();
    }

    @AfterAll
    static void arreter() {
        SERVICES.stop();
    }

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry proprietes) {
        proprietes.add("sahti.cnam.url", () -> SERVICES.baseUrl() + "/ws");
        proprietes.add("sahti.portail.url", SERVICES::baseUrl);
        proprietes.add("sahti.dashboard.url", () -> SERVICES.baseUrl() + "/graphql");
    }

    // ===================== Réponses simulées =====================

    private static final String SOAP_DEBUT = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<SOAP-ENV:Body>";
    private static final String SOAP_FIN = "</SOAP-ENV:Body></SOAP-ENV:Envelope>";
    private static final String ASSURE = "<ns2:assure><ns2:identifiantPatient>PAT-0001</ns2:identifiantPatient>"
            + "<ns2:nom>Ben Salah</ns2:nom><ns2:prenom>Ahmed</ns2:prenom><ns2:dateNaissance>1958-03-14</ns2:dateNaissance>"
            + "<ns2:matriculeCNAM>CNAM-1958-0001</ns2:matriculeCNAM><ns2:codeVignetteCNAM>VG-2026-000001</ns2:codeVignetteCNAM>"
            + "<ns2:filiere>PUBLIQUE</ns2:filiere><ns2:dateFinValidite>2027-12-31</ns2:dateFinValidite><ns2:apci>true</ns2:apci>"
            + "</ns2:assure>";

    private static String soap(String corps) {
        return SOAP_DEBUT + corps + SOAP_FIN;
    }

    private static void stubSoap(String operation, String reponse) {
        SERVICES.stubFor(post(urlEqualTo("/ws")).withRequestBody(containing(operation + "Request"))
                .willReturn(aResponse().withHeader("Content-Type", "text/xml;charset=utf-8").withBody(soap(reponse))));
    }

    private static void stubGraphQl(String operation, String donnees) {
        SERVICES.stubFor(post(urlEqualTo("/graphql")).withRequestBody(containing("mutation " + operation))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"" + operation + "\":" + donnees + "}}")));
    }

    private static void stubServicesNominaux() {
        stubSoap("consulterHistorique", "<ns2:consulterHistoriqueResponse xmlns:ns2=\"http://sahticonnect.tn/cnam\">"
                + ASSURE + "</ns2:consulterHistoriqueResponse>");
        stubSoap("verifierDroitsCNAM", "<ns2:verifierDroitsCNAMResponse xmlns:ns2=\"http://sahticonnect.tn/cnam\">" + ASSURE
                + "<ns2:couverture><ns2:couvert>true</ns2:couvert><ns2:tauxRemboursement>100</ns2:tauxRemboursement>"
                + "<ns2:motif>APCI 100 %</ns2:motif></ns2:couverture><ns2:montantEstime>55.000</ns2:montantEstime>"
                + "<ns2:partCNAMEstimee>55.000</ns2:partCNAMEstimee><ns2:partPatientEstimee>0.000</ns2:partPatientEstimee>"
                + "</ns2:verifierDroitsCNAMResponse>");
        stubSoap("emettreFeuilleDeSoins", "<ns2:emettreFeuilleDeSoinsResponse xmlns:ns2=\"http://sahticonnect.tn/cnam\">"
                + "<ns2:feuilleDeSoins><ns2:numero>FS-2026-000003</ns2:numero><ns2:identifiantPatient>PAT-0001</ns2:identifiantPatient>"
                + "<ns2:dateSoins>2026-11-24</ns2:dateSoins><ns2:etablissement>SahtiConnect</ns2:etablissement>"
                + "<ns2:medecin>Équipe</ns2:medecin><ns2:lignes><ns2:acte><ns2:lettreCle>V</ns2:lettreCle>"
                + "<ns2:coefficient>1</ns2:coefficient></ns2:acte><ns2:montant>55.000</ns2:montant></ns2:lignes>"
                + "<ns2:montantTotal>145.000</ns2:montantTotal><ns2:tauxApplique>100</ns2:tauxApplique>"
                + "<ns2:partCNAM>145.000</ns2:partCNAM><ns2:partPatient>0.000</ns2:partPatient><ns2:statut>CERTIFIEE</ns2:statut>"
                + "<ns2:codeCertification>abc</ns2:codeCertification></ns2:feuilleDeSoins></ns2:emettreFeuilleDeSoinsResponse>");
        SERVICES.stubFor(post(urlEqualTo("/urgences/ambulances")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"AMB-2026-000007\",\"identifiantPatient\":\"PAT-0001\",\"statut\":\"DEPECHEE\","
                        + "\"vehicule\":\"SAMU-TN-01\",\"delaiEstimeMinutes\":12,\"etablissementDestination\":\"Hôpital Charles Nicolle\","
                        + "\"dateDemande\":\"2026-11-24T09:48:00+01:00\"}")));
        SERVICES.stubFor(post(urlEqualTo("/rendez-vous")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"RDV-2026-000043\",\"identifiantPatient\":\"PAT-0001\",\"medecinId\":\"MED-001\","
                        + "\"debut\":\"2026-11-24T10:15:00+01:00\",\"fin\":\"2026-11-24T10:45:00+01:00\",\"type\":\"URGENCE\","
                        + "\"statut\":\"CONFIRME\"}")));
        stubGraphQl("enregistrerAlerte", "{\"id\":\"ALR\",\"statut\":\"NOUVELLE\"}");
        stubGraphQl("creerIntervention", "{\"id\":\"INT-2026-000004\",\"etat\":\"INTERVENTION_DECLENCHEE\",\"statutFacturation\":\"EN_ATTENTE\"}");
        stubGraphQl("mettreAJourIntervention", "{\"id\":\"INT-2026-000004\",\"etat\":\"FACTUREE\",\"statutFacturation\":\"FACTUREE\"}");
    }

    @BeforeEach
    void reinitialiser() {
        SERVICES.resetAll();
        stubServicesNominaux();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ===================== Utilitaires =====================

    private String nouvelleAlerte() {
        return "ALR-2026-%06d".formatted(ALERTES.incrementAndGet());
    }

    private String declencher(String alerteId, String niveau) throws Exception {
        String location = mvc.perform(MockMvcRequestBuilders.post("/workflows/declencher").contentType(MediaType.APPLICATION_JSON).content("""
                        {"alerteId":"%s","patientId":"PAT-0001","niveau":"%s","typeMesure":"FREQUENCE_CARDIAQUE",
                         "valeur":142,"seuilFranchi":"FC > 130","message":"Alerte %s","horodatage":"2026-11-24T09:47:58+01:00"}"""
                        .formatted(alerteId, niveau, niveau)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", matchesPattern("/workflows/WF-\\d{4}-\\d{6}")))
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private Workflow attendreFin(String id) {
        await().atMost(Duration.ofSeconds(20)).until(() -> repository.findById(id)
                .map(w -> !w.isEnCours() && (w.getEtat() == EtatWorkflow.FACTUREE || w.getEtat() == EtatWorkflow.ECHEC))
                .orElse(false));
        return repository.findById(id).orElseThrow();
    }

    // ===================== Scénarios =====================

    @Test
    void critiqueCouvert_lesQuatreProtocolesAppeles_facturee() throws Exception {
        String alerteId = nouvelleAlerte();
        String id = declencher(alerteId, "CRITIQUE");

        Workflow wf = attendreFin(id);
        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(wf.getReferenceExterne()).isEqualTo("AMB-2026-000007");
        assertThat(wf.getNumeroFeuilleSoins()).isEqualTo("FS-2026-000003");

        SERVICES.verify(1, postRequestedFor(urlEqualTo("/urgences/ambulances"))
                .withRequestBody(matchingJsonPath("$.referenceAlerte", equalTo(alerteId))));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/ws")).withRequestBody(containing("<ns2:lettreCle>V</ns2:lettreCle>"))
                .withRequestBody(containing("verifierDroitsCNAMRequest")));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/ws")).withRequestBody(containing("emettreFeuilleDeSoinsRequest"))
                .withRequestBody(containing("INT-2026-000004")));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("mutation enregistrerAlerte"))
                .withRequestBody(containing(alerteId)));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("mutation creerIntervention"))
                .withRequestBody(containing("AMB-2026-000007")));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("mutation mettreAJourIntervention"))
                .withRequestBody(containing("FS-2026-000003")));

        mvc.perform(get("/workflows/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("FACTUREE"))
                .andExpect(jsonPath("$.etapes.length()").value(5))
                .andExpect(jsonPath("$.etapes[4].message").value(org.hamcrest.Matchers.containsString("CERTIFIEE")))
                .andExpect(jsonPath("$.montantTotal").value(145.0));
    }

    @Test
    void memeAlerteDeclencheeDeuxFois_200EtMemeWorkflow() throws Exception {
        String alerteId = nouvelleAlerte();
        String id = declencher(alerteId, "CRITIQUE");

        mvc.perform(MockMvcRequestBuilders.post("/workflows/declencher").contentType(MediaType.APPLICATION_JSON).content("""
                        {"alerteId":"%s","patientId":"PAT-0001","niveau":"CRITIQUE","typeMesure":"FREQUENCE_CARDIAQUE",
                         "valeur":142,"seuilFranchi":"FC > 130","horodatage":"2026-11-24T09:47:58+01:00"}""".formatted(alerteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        attendreFin(id);
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/urgences/ambulances")));
    }

    @Test
    void soapEnPanne_retryPuisOnContinue_aRegulariser() throws Exception {
        SERVICES.stubFor(post(urlEqualTo("/ws")).willReturn(aResponse().withStatus(503)));

        Workflow wf = attendreFin(declencher(nouvelleAlerte(), "CRITIQUE"));

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.ECHEC);
        assertThat(wf.getEtapeEnEchec()).isEqualTo(NomEtape.DROITS_VERIFIES);
        // consulterHistorique est idempotent : 3 tentatives (retry) à l'étape DROITS_VERIFIES
        assertThat(SERVICES.findAll(postRequestedFor(urlEqualTo("/ws")).withRequestBody(containing("consulterHistoriqueRequest"))))
                .hasSizeGreaterThanOrEqualTo(3);
        // l'ambulance est quand même envoyée, la fiche signale une facturation à régulariser
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/urgences/ambulances")));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("mutation creerIntervention"))
                .withRequestBody(matchingJsonPath("$.variables.input.statutFacturation", equalTo("A_REGULARISER"))));
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/ws")).withRequestBody(containing("emettreFeuilleDeSoinsRequest")));
    }

    @Test
    void soapFaultClient_codeLuDansLeDetail_pasDeRetry() throws Exception {
        SERVICES.stubFor(post(urlEqualTo("/ws")).withRequestBody(containing("verifierDroitsCNAMRequest"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml;charset=utf-8").withBody(soap(
                        "<SOAP-ENV:Fault><faultcode>SOAP-ENV:Client</faultcode>"
                                + "<faultstring xml:lang=\"fr\">La vignette VG-2026-000001 ne correspond pas au patient PAT-0001</faultstring>"
                                + "<detail><ns2:verifierDroitsCNAMFault xmlns:ns2=\"http://sahticonnect.tn/cnam\">"
                                + "<ns2:code>VIGNETTE_INVALIDE</ns2:code><ns2:message>La vignette ne correspond pas</ns2:message>"
                                + "</ns2:verifierDroitsCNAMFault></detail></SOAP-ENV:Fault>"))));

        Workflow wf = attendreFin(declencher(nouvelleAlerte(), "CRITIQUE"));

        assertThat(wf.derniere(NomEtape.DROITS_VERIFIES).orElseThrow().getMessage()).contains("VIGNETTE_INVALIDE");
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/ws")).withRequestBody(containing("verifierDroitsCNAMRequest")));
    }

    @Test
    void aucunVehicule409_repliSurRendezVousUrgence_pasDeRetry() throws Exception {
        SERVICES.stubFor(post(urlEqualTo("/urgences/ambulances")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"horodatage\":\"2026-11-24T10:15:00+01:00\",\"statut\":409,\"code\":\"AUCUN_VEHICULE_DISPONIBLE\","
                        + "\"message\":\"Les 5 ambulances sont déjà en mission\",\"chemin\":\"/urgences/ambulances\"}")));

        Workflow wf = attendreFin(declencher(nouvelleAlerte(), "CRITIQUE"));

        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(wf.getReferenceExterne()).isEqualTo("RDV-2026-000043");
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/urgences/ambulances")));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/rendez-vous"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("URGENCE"))));
    }

    @Test
    void reponseAbsenteSurUneCreation_pasRejouee_pourEviterUnDoublon() throws Exception {
        // la connexion est coupée APRÈS l'envoi : l'ambulance a peut-être été créée, on ne réessaie pas
        SERVICES.stubFor(post(urlEqualTo("/urgences/ambulances")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        Workflow wf = attendreFin(declencher(nouvelleAlerte(), "CRITIQUE"));

        assertThat(wf.getEtapeEnEchec()).isEqualTo(NomEtape.INTERVENTION_DECLENCHEE);
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/urgences/ambulances")));
        // le médecin est quand même prévenu
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("mutation creerIntervention"))
                .withRequestBody(matchingJsonPath("$.variables.input.etat", equalTo("ECHEC"))));
    }

    @Test
    void relanceApresPanneDuPortail() throws Exception {
        SERVICES.stubFor(post(urlEqualTo("/urgences/ambulances")).willReturn(aResponse().withStatus(503)));
        String id = declencher(nouvelleAlerte(), "CRITIQUE");
        assertThat(attendreFin(id).getEtat()).isEqualTo(EtatWorkflow.ECHEC);

        // le portail revient
        SERVICES.resetAll();
        stubServicesNominaux();
        mvc.perform(MockMvcRequestBuilders.post("/workflows/" + id + "/relancer"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.enCours").value(true));

        Workflow wf = attendreFin(id);
        assertThat(wf.getEtat()).isEqualTo(EtatWorkflow.FACTUREE);
        assertThat(wf.getTentative()).isEqualTo(2);
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("mutation enregistrerAlerte")));
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/graphql")).withRequestBody(containing("mutation creerIntervention")));

        mvc.perform(MockMvcRequestBuilders.post("/workflows/" + id + "/relancer"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKFLOW_NON_RELANCABLE"));
    }

    // ===================== API =====================

    @Test
    void erreursDuContrat() throws Exception {
        mvc.perform(get("/workflows/WF-2026-999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKFLOW_INTROUVABLE"))
                .andExpect(jsonPath("$.chemin").value("/workflows/WF-2026-999999"));
        mvc.perform(get("/workflows/WF-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        mvc.perform(MockMvcRequestBuilders.post("/workflows/declencher").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alerteId\":\"ALERTE\",\"patientId\":\"PAT-0001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].champ", org.hamcrest.Matchers.hasItem("alerteId")));
        mvc.perform(MockMvcRequestBuilders.post("/workflows/WF-2026-999999/relancer"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/workflows").param("patientId", "X"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listeFiltreeLaPlusRecenteEnPremier() throws Exception {
        String premier = declencher(nouvelleAlerte(), "CRITIQUE");
        String second = declencher(nouvelleAlerte(), "ELEVEE");
        attendreFin(premier);
        attendreFin(second);

        mvc.perform(get("/workflows").param("patientId", "PAT-0001").param("etat", "FACTUREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[*].etat", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("FACTUREE"))));
    }
}
