package tn.sahticonnect.cnam.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adresse du service annoncée dans le WSDL (transformWsdlLocations + forward-headers-strategy=framework) :
 * celle par laquelle le WSDL a été lu, en direct comme via l'API Gateway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WsdlAdresseIntegrationTest {

    @LocalServerPort
    private int port;

    private String wsdl(RestClient.RequestHeadersSpec<?> requete) {
        return requete.retrieve().body(String.class);
    }

    @Test
    void wsdlLuEnDirect_annonceLAdresseDirecte() {
        String wsdl = wsdl(RestClient.create().get().uri("http://localhost:" + port + "/ws/dossier-cnam.wsdl"));

        assertThat(wsdl).contains("<soap:address location=\"http://localhost:" + port + "/ws\"/>");
    }

    @Test
    void wsdlLuViaLaGateway_annonceLAdresseDeLaGateway() {
        // en-têtes ajoutés par Spring Cloud Gateway pour GET http://localhost:8080/api/cnam/ws/dossier-cnam.wsdl
        String wsdl = wsdl(RestClient.create().get().uri("http://localhost:" + port + "/ws/dossier-cnam.wsdl")
                .header("X-Forwarded-Host", "localhost:8080")
                .header("X-Forwarded-Port", "8080")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Prefix", "/api/cnam"));

        assertThat(wsdl).contains("<soap:address location=\"http://localhost:8080/api/cnam/ws\"/>")
                .doesNotContain(":" + port + "/");
    }
}
