package tn.sahticonnect.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Services arrêtés (port fermé) : fallback des circuit breakers, au format du contrat. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sahti.securite.active=false") // routage seul : la sécurité est testée dans securite/
class FallbackIntegrationTest {

    @DynamicPropertySource
    static void servicesArretes(DynamicPropertyRegistry registre) throws IOException {
        int portFerme;
        try (ServerSocket s = new ServerSocket(0)) {
            portFerme = s.getLocalPort();   // libéré aussitôt : personne n'écoute plus sur ce port
        }
        for (String service : List.of("portail-patient", "orchestrateur", "dashboard-medical", "dossier-cnam")) {
            registre.add("sahti.services." + service, () -> "http://localhost:" + portFerme);
        }
    }

    private WebTestClient client;

    /** Délai du client de test > timeout de la gateway (5 s) : la 1re requête d'un contexte froid peut être lente sur une machine chargée. */
    @Autowired
    void setClient(WebTestClient client) {
        this.client = client.mutate().responseTimeout(Duration.ofSeconds(15)).build();
    }

    @Test
    void rest_503AuFormatErreur_avecService() {
        client.get().uri("/api/portail/patients/PAT-0001").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.statut").isEqualTo(503)
                .jsonPath("$.code").isEqualTo("SERVICE_INDISPONIBLE")
                .jsonPath("$.service").isEqualTo("portail-patient")
                .jsonPath("$.chemin").isEqualTo("/api/portail/patients/PAT-0001")
                .jsonPath("$.horodatage").isNotEmpty()
                .jsonPath("$.message").value(m -> assertThat((String) m).startsWith("Service portail-patient indisponible"));
    }

    @Test
    void orchestrateurEtGraphql_503() {
        client.get().uri("/api/orchestrateur/workflows").exchange()
                .expectStatus().isEqualTo(503).expectBody().jsonPath("$.service").isEqualTo("orchestrateur");
        client.get().uri("/api/orchestrateur/workflows/flux").exchange()
                .expectStatus().isEqualTo(503).expectBody().jsonPath("$.service").isEqualTo("orchestrateur");
        client.post().uri("/api/graphql").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"query\":\"{ patients { id } }\"}")
                .exchange()
                .expectStatus().isEqualTo(503).expectBody().jsonPath("$.service").isEqualTo("dashboard-medical");
    }

    @Test
    void soap_faultServer() {
        client.post().uri("/api/cnam/ws").contentType(MediaType.TEXT_XML).bodyValue("<soap/>").exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_XML)
                .expectBody(String.class).value(xml -> assertThat(xml)
                        .contains("<faultcode>SOAP-ENV:Server</faultcode>")
                        .contains("<gw:code>SERVICE_INDISPONIBLE</gw:code>")
                        .contains("<gw:service>dossier-cnam</gw:service>"));
    }

    @Test
    void echecsRepetes_circuitOuvert() {
        for (int i = 0; i < 6; i++) {
            client.get().uri("/api/portail/medecins").exchange().expectStatus().isEqualTo(503);
        }
        client.get().uri("/api/portail/medecins").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").value(m -> assertThat((String) m).contains("circuit ouvert"));
    }
}
