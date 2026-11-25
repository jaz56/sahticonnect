package tn.sahticonnect.gateway.securite;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sécurité de la gateway avec de VRAIS jetons signés : matrice rôle × route, 401 / 403 au format des contrats,
 * piège de l'issuer (clés lues à une adresse interne, issuer public validé), propagation de l'identité.
 * WireMock joue à la fois les 4 services et l'endpoint JWKS de Keycloak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "sahti.securite.active=true")
class SecuriteIntegrationTest {

    @RegisterExtension
    static WireMockExtension services = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) {
        for (String service : List.of("portail-patient", "orchestrateur", "dashboard-medical", "dossier-cnam")) {
            registre.add("sahti.services." + service, services::baseUrl);
        }
        // clés à l'adresse « interne » (ici WireMock), issuer à l'adresse publique de Keycloak (jamais appelée)
        registre.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> services.baseUrl() + Jetons.CHEMIN_JWKS);
        registre.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> Jetons.ISSUER_PUBLIC);
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void preparer() {
        client = client.mutate().responseTimeout(Duration.ofSeconds(20)).build();
        services.resetAll();
        services.stubFor(get(Jetons.CHEMIN_JWKS).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(Jetons.jwks())));
        services.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{}")));
        services.stubFor(get("/openapi/portail-patient.yaml").willReturn(aResponse()
                .withHeader("Content-Type", "application/yaml").withBody("openapi: 3.0.3\nservers:\n  - url: x\npaths: {}\n")));
    }

    private static String jetonPour(String qui) {
        return switch (qui) {
            case "ANONYME" -> null;
            case "PATIENT" -> Jetons.patient("ahmed", "PAT-0001");
            default -> Jetons.role(qui.toLowerCase(), qui);
        };
    }

    private WebTestClient.ResponseSpec appeler(String qui, String methode, String chemin) {
        WebTestClient.RequestBodySpec requete = client.method(HttpMethod.valueOf(methode)).uri(chemin);
        String jeton = jetonPour(qui);
        if (jeton != null) {
            requete.header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton);
        }
        if (methode.equals("POST")) {
            requete.contentType(chemin.contains("cnam") ? MediaType.TEXT_XML : MediaType.APPLICATION_JSON).bodyValue(
                    chemin.contains("cnam") ? "<soap/>" : "{}");
        }
        return requete.exchange();
    }

    // ===================== Matrice des droits =====================

    @ParameterizedTest(name = "{0} {1} {2} → {3}")
    @CsvSource({
            // routes publiques
            "ANONYME, GET,  /actuator/health,                              200",
            "ANONYME, GET,  /v3/api-docs/swagger-config,                   200",
            "ANONYME, GET,  /api/portail/openapi/portail-patient.yaml,     200",
            "ANONYME, GET,  /api/cnam/ws/dossier-cnam.wsdl,                200",
            // sans jeton : 401
            "ANONYME, GET,  /api/portail/medecins,                         401",
            "ANONYME, POST, /api/graphql,                                  401",
            "ANONYME, POST, /api/cnam/ws,                                  401",
            "ANONYME, POST, /api/orchestrateur/workflows/declencher,       401",
            "ANONYME, GET,  /api/orchestrateur/workflows,                  401",
            "ANONYME, GET,  /api/orchestrateur/workflows/flux,             401",
            "ANONYME, GET,  /actuator/gateway/routes,                      401",
            // PATIENT : portail seulement
            "PATIENT, GET,  /api/portail/medecins,                         200",
            "PATIENT, POST, /api/graphql,                                  403",
            "PATIENT, POST, /api/cnam/ws,                                  403",
            "PATIENT, GET,  /api/cnam/ws/dossier-cnam.wsdl,                200",
            "PATIENT, POST, /api/orchestrateur/workflows/declencher,       403",
            "PATIENT, GET,  /api/orchestrateur/workflows,                  403",
            "PATIENT, GET,  /actuator/gateway/routes,                      403",
            // MEDECIN : portail, GraphQL, déclenchement d'alerte
            "MEDECIN, GET,  /api/portail/medecins,                         200",
            "MEDECIN, POST, /api/graphql,                                  200",
            "MEDECIN, POST, /api/cnam/ws,                                  403",
            "MEDECIN, POST, /api/orchestrateur/workflows/declencher,       200",
            "MEDECIN, GET,  /api/orchestrateur/workflows,                  403",
            "MEDECIN, GET,  /api/orchestrateur/workflows/flux,             403",
            // ADMIN : tout
            "ADMIN,   GET,  /api/portail/medecins,                         200",
            "ADMIN,   POST, /api/graphql,                                  200",
            "ADMIN,   POST, /api/cnam/ws,                                  200",
            "ADMIN,   POST, /api/orchestrateur/workflows/declencher,       200",
            "ADMIN,   GET,  /api/orchestrateur/workflows,                  200",
            "ADMIN,   GET,  /actuator/gateway/routes,                      200",
    })
    void matriceDesDroits(String qui, String methode, String chemin, int statut) {
        appeler(qui, methode, chemin).expectStatus().isEqualTo(statut);
    }

    // ===================== Format des erreurs =====================

    @Test
    void sansJeton_401NonAuthentifie_auFormatErreur_avecCorsEtCorrelation() {
        client.get().uri("/api/portail/medecins").header("Origin", "http://localhost:4200").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer realm=\"sahticonnect\"")
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200")
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.statut").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("NON_AUTHENTIFIE")
                .jsonPath("$.chemin").isEqualTo("/api/portail/medecins");
    }

    @Test
    void roleInsuffisant_403AccesRefuse() {
        appeler("PATIENT", "GET", "/api/orchestrateur/workflows")
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCES_REFUSE")
                .jsonPath("$.message").value(m -> assertThat((String) m).contains("/api/orchestrateur/workflows"));
    }

    @Test
    void routeSoap_401Et403EnSoapFault() {
        appeler("ANONYME", "POST", "/api/cnam/ws").expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_XML)
                .expectBody(String.class).value(xml -> assertThat(xml)
                        .contains("<faultcode>SOAP-ENV:Client</faultcode>", "<gw:code>NON_AUTHENTIFIE</gw:code>"));
        appeler("MEDECIN", "POST", "/api/cnam/ws").expectStatus().isForbidden()
                .expectBody(String.class).value(xml -> assertThat(xml).contains("<gw:code>ACCES_REFUSE</gw:code>"));
    }

    // ===================== Validation du jeton (piège de l'issuer) =====================

    @Test
    void issuerPublic_accepte_alorsQueLesClesSontLuesAUneAutreAdresse() {
        client.get().uri("/api/portail/medecins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + Jetons.avecIssuer(Jetons.ISSUER_PUBLIC))
                .exchange().expectStatus().isOk();
        services.verify(getRequestedFor(urlEqualTo(Jetons.CHEMIN_JWKS)));
    }

    @Test
    void jetonsInvalides_401() {
        for (String jeton : List.of(
                Jetons.avecIssuer(Jetons.ISSUER_INTERNE),       // issuer interne : refusé (le navigateur n'en a jamais)
                Jetons.avecAudience("autre-api"),               // jeton destiné à une autre application
                Jetons.expire(),
                Jetons.signeParUneAutreCle(),                   // signature non vérifiable avec les clés du realm
                "pas.un.jwt")) {
            client.get().uri("/api/portail/medecins").header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton).exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("NON_AUTHENTIFIE")
                    .jsonPath("$.message").value(m -> assertThat((String) m).contains("Jeton invalide"));
        }
    }

    @Test
    void jetonDansLUrl_ignoreHorsWebSocket() {
        client.get().uri("/api/portail/medecins?access_token=" + Jetons.role("admin", "ADMIN")).exchange()
                .expectStatus().isUnauthorized();
    }

    // ===================== Propagation de l'identité =====================

    @Test
    void patient_xPatientIdInjecte_lesFauxEnTetesSupprimes() {
        client.get().uri("/api/portail/rendez-vous")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + Jetons.patient("ahmed", "PAT-0001"))
                .header("X-Patient-Id", "PAT-0002")     // tentative d'usurpation
                .header("X-Roles", "ADMIN")
                .exchange().expectStatus().isOk();

        services.verify(getRequestedFor(urlPathEqualTo("/rendez-vous"))
                .withHeader("X-Patient-Id", equalTo("PAT-0001"))
                .withHeader("X-Roles", equalTo("PATIENT")));
    }

    @Test
    void medecin_pasDeXPatientId_meme_siLeClientEnEnvoieUn() {
        client.get().uri("/api/portail/patients/PAT-0002")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + Jetons.role("dr.chaabane", "MEDECIN"))
                .header("X-Patient-Id", "PAT-0001")
                .exchange().expectStatus().isOk();

        services.verify(getRequestedFor(urlPathEqualTo("/patients/PAT-0002"))
                .withHeader("X-Patient-Id", absent())
                .withHeader("X-Roles", equalTo("MEDECIN")));
    }

    @Test
    void routePublique_enTetesDIdentiteDuClientSupprimes() {
        client.get().uri("/api/cnam/ws/dossier-cnam.wsdl").header("X-Patient-Id", "PAT-0001").header("X-Roles", "ADMIN")
                .exchange().expectStatus().isOk();

        services.verify(getRequestedFor(urlPathEqualTo("/ws/dossier-cnam.wsdl"))
                .withHeader("X-Patient-Id", absent())
                .withHeader("X-Roles", absent()));
    }

    @Test
    void jetonPatientSansPatientId_refuse() {
        client.get().uri("/api/portail/rendez-vous")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + Jetons.patientSansPatientId())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("ACCES_REFUSE");
        services.verify(0, getRequestedFor(urlPathEqualTo("/rendez-vous")));
    }

    // ===================== CORS =====================

    @Test
    void preflight_enTeteAuthorizationAutorise() {
        client.options().uri("/api/graphql")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization, content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200")
                .expectHeader().value("Access-Control-Allow-Headers", h -> assertThat(h.toLowerCase()).contains("authorization"));
    }
}
