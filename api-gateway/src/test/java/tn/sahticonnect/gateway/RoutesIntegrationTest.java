package tn.sahticonnect.gateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les 4 routes HTTP contre un WireMock qui joue les 4 services : retrait du préfixe, en-têtes transmis, CORS,
 * X-Correlation-Id, timeout de 5 s, réécriture des contrats OpenAPI et relais du SSE sans buffering ni timeout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sahti.securite.active=false") // routage seul : la sécurité est testée dans securite/
class RoutesIntegrationTest {

    @RegisterExtension
    static WireMockExtension services = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) {
        for (String service : List.of("portail-patient", "orchestrateur", "dashboard-medical", "dossier-cnam")) {
            registre.add("sahti.services." + service, services::baseUrl);
        }
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void delaiLong() {
        client = client.mutate().responseTimeout(Duration.ofSeconds(30)).build();
    }

    // ===================== Routes et retrait du préfixe =====================

    @Test
    void portail_prefixeApiPortailRetire() {
        services.stubFor(get("/patients/PAT-0001").willReturn(okJson("{\"id\":\"PAT-0001\",\"nom\":\"Ben Salah\"}")));

        client.get().uri("/api/portail/patients/PAT-0001").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.nom").isEqualTo("Ben Salah");

        services.verify(getRequestedFor(urlEqualTo("/patients/PAT-0001")));
    }

    @Test
    void orchestrateur_prefixeRetire_parametresConserves() {
        services.stubFor(get(urlPathEqualTo("/workflows")).willReturn(okJson("[]")));

        client.get().uri("/api/orchestrateur/workflows?patientId=PAT-0003&etat=ECHEC").exchange().expectStatus().isOk();

        services.verify(getRequestedFor(urlEqualTo("/workflows?patientId=PAT-0003&etat=ECHEC")));
    }

    @Test
    void graphql_apiGraphqlVersGraphql() {
        services.stubFor(post("/graphql").willReturn(okJson("{\"data\":{\"patient\":{\"nom\":\"Ben Salah\"}}}")));

        client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"{ patient(id: \\\"PAT-0001\\\") { nom } }\"}").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.patient.nom").isEqualTo("Ben Salah");

        services.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(matching(".*PAT-0001.*")));
    }

    @Test
    void cnam_soapEtWsdl_versWs_avecEnTetesForwarded() {
        services.stubFor(post("/ws").willReturn(aResponse().withHeader("Content-Type", "text/xml").withBody("<ok/>")));
        services.stubFor(get("/ws/dossier-cnam.wsdl").willReturn(aResponse().withHeader("Content-Type", "text/xml")
                .withBody("<wsdl:definitions/>")));

        client.post().uri("/api/cnam/ws").contentType(MediaType.TEXT_XML).bodyValue("<soap/>").exchange()
                .expectStatus().isOk();
        client.get().uri("/api/cnam/ws/dossier-cnam.wsdl").exchange().expectStatus().isOk();

        services.verify(postRequestedFor(urlEqualTo("/ws")));
        // ces en-têtes permettent à dossier-cnam d'écrire l'adresse de la gateway dans le WSDL
        services.verify(getRequestedFor(urlEqualTo("/ws/dossier-cnam.wsdl"))
                .withHeader("X-Forwarded-Prefix", equalTo("/api/cnam"))
                .withHeader("X-Forwarded-Host", matching("localhost:\\d+"))
                .withHeader("X-Forwarded-Proto", equalTo("http")));
    }

    @Test
    void enTetesForwardedEnvoyesParLeClient_ignores() {
        services.stubFor(get("/ws/dossier-cnam.wsdl").willReturn(aResponse().withBody("<wsdl:definitions/>")));

        client.get().uri("/api/cnam/ws/dossier-cnam.wsdl")
                .header("X-Forwarded-Host", "site-malveillant.example")
                .header("X-Forwarded-Prefix", "/piege")
                .exchange().expectStatus().isOk();

        services.verify(getRequestedFor(urlEqualTo("/ws/dossier-cnam.wsdl"))
                .withHeader("X-Forwarded-Host", matching("localhost:\\d+"))
                .withHeader("X-Forwarded-Prefix", equalTo("/api/cnam")));
    }

    @Test
    void routeInconnue_404() {
        client.get().uri("/api/telemetrie/mesures").exchange().expectStatus().isNotFound();
    }

    // ===================== CORS =====================

    @Test
    void preflightCors_clientAngular_accepte_sansAppelerLeService() {
        client.options().uri("/api/portail/rendez-vous")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200")
                .expectHeader().value("Access-Control-Allow-Methods", m -> assertThat(m).contains("POST"))
                .expectHeader().value("Access-Control-Allow-Headers", h -> assertThat(h).containsIgnoringCase("content-type"));

        assertThat(services.getAllServeEvents()).noneMatch(e -> e.getRequest().getMethod().getName().equals("OPTIONS"));
    }

    @Test
    void preflightCors_autreOrigine_refuse() {
        client.options().uri("/api/portail/rendez-vous")
                .header("Origin", "http://site-malveillant.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }

    @Test
    void requeteCors_enTetesExposes() {
        services.stubFor(get("/medecins").willReturn(okJson("[]")));

        client.get().uri("/api/portail/medecins").header("Origin", "http://localhost:4200").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200")
                .expectHeader().value("Access-Control-Expose-Headers", h -> assertThat(h).contains("X-Correlation-Id"));
    }

    // ===================== X-Correlation-Id =====================

    @Test
    void correlationId_genereSiAbsent_transmisEtRenvoye() {
        services.stubFor(get("/medecins").willReturn(okJson("[]")));

        String id = client.get().uri("/api/portail/medecins").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-Id");

        assertThat(id).matches("[0-9a-f-]{36}");
        services.verify(getRequestedFor(urlEqualTo("/medecins")).withHeader("X-Correlation-Id", equalTo(id)));
    }

    @Test
    void correlationId_fourniParLeClient_conserve() {
        services.stubFor(get("/workflows/WF-2026-000001").willReturn(okJson("{}")));

        client.get().uri("/api/orchestrateur/workflows/WF-2026-000001").header("X-Correlation-Id", "demo-42").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "demo-42");

        services.verify(getRequestedFor(urlEqualTo("/workflows/WF-2026-000001"))
                .withHeader("X-Correlation-Id", equalTo("demo-42")));
    }

    @Test
    void correlationId_invalide_remplace() {
        services.stubFor(get("/medecins").willReturn(okJson("[]")));

        String id = client.get().uri("/api/portail/medecins").header("X-Correlation-Id", "a b<script>").exchange()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-Id");

        assertThat(id).matches("[0-9a-f-]{36}");
    }

    // ===================== Timeout de 5 s =====================

    @Test
    void serviceTropLent_timeout5s_fallback503() {
        services.stubFor(get("/ordonnances-lentes").willReturn(okJson("[]").withFixedDelay(6_000)));

        client.get().uri("/api/portail/ordonnances-lentes").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_INDISPONIBLE")
                .jsonPath("$.service").isEqualTo("portail-patient")
                .jsonPath("$.message").value(m -> assertThat((String) m).contains("délai"));
    }

    @Test
    void graphql_reponseApres6s_attendueJusqua10s() {
        // réponse partielle du dashboard quand un service distant est en panne (2 appels × 3 s) : pas de 503
        services.stubFor(post("/graphql").withRequestBody(matching(".*partielle.*"))
                .willReturn(okJson("{\"data\":{\"patient\":{\"couvert\":null}},\"errors\":[{\"message\":\"Service dossier-cnam indisponible\"}]}")
                        .withFixedDelay(6_500)));

        client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"query\":\"{ partielle }\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.errors[0].message").isEqualTo("Service dossier-cnam indisponible");
    }

    // ===================== SSE : ni buffering, ni timeout =====================

    @Test
    void fluxSse_relayeAuFilDeLEau_auDelaDu_timeoutDe5s() {
        // deux événements de même taille, envoyés à 6 s d'intervalle (plus que le timeout de réponse de 5 s)
        String evenement1 = "event:workflow\ndata:{\"id\":\"WF-2026-000001\",\"etat\":\"ALERTE_RECUE\"}\n\n";
        String evenement2 = "event:workflow\ndata:{\"id\":\"WF-2026-000001\",\"etat\":\"FACTUREE\"}    \n\n";
        assertThat(evenement1).hasSameSizeAs(evenement2);
        services.stubFor(get("/workflows/flux").willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody(evenement1 + evenement2)
                .withChunkedDribbleDelay(2, 12_000)));

        Flux<ServerSentEvent<String>> flux = client.get().uri("/api/orchestrateur/workflows/flux")
                .accept(MediaType.TEXT_EVENT_STREAM).exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() { }).getResponseBody()
                .filter(evenement -> evenement.data() != null);
        List<Tuple2<Long, ServerSentEvent<String>>> recus = flux.elapsed().collectList().block(Duration.ofSeconds(30));

        assertThat(recus).hasSize(2);
        assertThat(recus.get(0).getT2().data()).contains("ALERTE_RECUE");
        assertThat(recus.get(1).getT2().data()).contains("FACTUREE");
        // le 1er événement arrive avant la fin du flux (pas de buffering), le 2e malgré 6 s de silence (pas de timeout)
        assertThat(recus.get(1).getT1()).as("silence entre les deux événements (ms)").isGreaterThan(5_000);
    }

    // ===================== Swagger UI regroupé =====================

    @Test
    void contratOpenApi_relayeAvecLAdresseDeLaGateway() {
        services.stubFor(get("/openapi/portail-patient.yaml").willReturn(aResponse()
                .withHeader("Content-Type", "application/yaml")
                .withBody("openapi: 3.0.3\nservers:\n  - url: http://localhost:8082\n    description: direct\ntags: []\npaths: {}\n")));

        client.get().uri("/api/portail/openapi/portail-patient.yaml").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(yaml -> assertThat(yaml)
                        .contains("servers:\n  - url: /api/portail\n")
                        .doesNotContain("8082")
                        .contains("tags: []", "paths: {}"));
    }

    @Test
    void swaggerUi_listeLesDeuxContrats() {
        client.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls.length()").isEqualTo(2)
                .jsonPath("$.urls[?(@.name == 'portail-patient')].url").isEqualTo("/api/portail/openapi/portail-patient.yaml")
                .jsonPath("$.urls[?(@.name == 'orchestrateur')].url").isEqualTo("/api/orchestrateur/openapi/orchestrateur.yaml")
                .jsonPath("$['urls.primaryName']").isEqualTo("portail-patient");
    }

    // ===================== Actuator =====================

    @Test
    void actuator_santeLisibleDepuisLeClientAngular() {
        client.get().uri("/actuator/health").header("Origin", "http://localhost:4200").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");
    }

    @Test
    void actuator_santeEtRoutes() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/actuator/gateway/routes").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[*].route_id").value(ids -> assertThat((List<String>) ids).contains(
                        "portail-patient", "orchestrateur", "orchestrateur-flux", "dashboard-medical", "dossier-cnam",
                        "portail-patient-openapi", "orchestrateur-openapi"));
    }
}
