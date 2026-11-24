package tn.sahticonnect.gateway.filtre;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Réécriture de « servers » sur les VRAIS contrats de docs/openapi : seule cette section change. */
class ServeursOpenApiTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({"portail-patient.yaml, /api/portail, http://localhost:8082",
            "orchestrateur.yaml, /api/orchestrateur, http://localhost:8085"})
    void seulLaSectionServersEstReecrite(String fichier, String prefixe, String adresseDirecte) throws IOException {
        String contrat = Files.readString(Path.of("..", "docs", "openapi", fichier)).replace("\r\n", "\n");

        String relaye = ServeursOpenApiGatewayFilterFactory.reecrire(contrat, prefixe);

        assertThat(contrat).contains("url: " + adresseDirecte);
        assertThat(relaye).contains("servers:\n  - url: " + prefixe + "\n    description: Via l'API Gateway\ntags:")
                .doesNotContain(adresseDirecte);
        // tout ce qui suit « tags: » est inchangé
        assertThat(relaye.substring(relaye.indexOf("\ntags:"))).isEqualTo(contrat.substring(contrat.indexOf("\ntags:")));
        assertThat(relaye.substring(0, relaye.indexOf("servers:"))).isEqualTo(contrat.substring(0, contrat.indexOf("servers:")));
    }
}
