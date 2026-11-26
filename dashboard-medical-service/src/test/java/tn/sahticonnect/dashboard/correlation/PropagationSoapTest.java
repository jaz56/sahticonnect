package tn.sahticonnect.dashboard.correlation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tn.sahticonnect.dashboard.client.CnamClient;
import tn.sahticonnect.dashboard.client.ModelesDistants.HistoriqueCnam;
import tn.sahticonnect.dashboard.config.SahtiProperties;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Appel SOAP du dashboard, comme pendant une requête GraphQL : lancé par l'exécuteur des appels distants (autre
 * thread) avec le vrai CnamClient ; la CNAM simulée (serveur HTTP du JDK) doit recevoir l'en-tête X-Correlation-Id.
 */
class PropagationSoapTest {

    private static final String REPONSE = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"><SOAP-ENV:Body>
            <ns2:consulterHistoriqueResponse xmlns:ns2="http://sahticonnect.tn/cnam"><ns2:assure>
            <ns2:identifiantPatient>PAT-0001</ns2:identifiantPatient><ns2:nom>Ben Salah</ns2:nom><ns2:prenom>Ahmed</ns2:prenom>
            <ns2:dateNaissance>1958-03-14</ns2:dateNaissance><ns2:matriculeCNAM>CNAM-1958-0001</ns2:matriculeCNAM>
            <ns2:codeVignetteCNAM>VG-2026-000001</ns2:codeVignetteCNAM><ns2:filiere>PUBLIQUE</ns2:filiere>
            <ns2:dateFinValidite>2027-12-31</ns2:dateFinValidite><ns2:apci>true</ns2:apci></ns2:assure>
            </ns2:consulterHistoriqueResponse></SOAP-ENV:Body></SOAP-ENV:Envelope>""";

    private final AtomicReference<String> enteteRecu = new AtomicReference<>();
    private HttpServer cnamSimulee;
    private ExecuteurAvecCorrelation executeur;
    private CnamClient client;

    @BeforeEach
    void demarrer() throws Exception {
        cnamSimulee = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        cnamSimulee.createContext("/ws", echange -> {
            enteteRecu.set(echange.getRequestHeaders().getFirst(Correlation.ENTETE));
            byte[] corps = REPONSE.getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().set("Content-Type", "text/xml;charset=utf-8");
            echange.sendResponseHeaders(200, corps.length);
            try (OutputStream sortie = echange.getResponseBody()) {
                sortie.write(corps);
            }
        });
        cnamSimulee.start();
        String url = "http://localhost:" + cnamSimulee.getAddress().getPort() + "/ws";
        client = new CnamClient(new SahtiProperties("Africa/Tunis", Duration.ofSeconds(3), null, new SahtiProperties.Cnam(url)));
        executeur = new ExecuteurAvecCorrelation(Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterEach
    void arreter() {
        cnamSimulee.stop(0);
        executeur.shutdownNow();
    }

    @Test
    void appelSoapSurUnAutreThread_enteteDeLaRequeteGraphQl() throws Exception {
        CompletableFuture<HistoriqueCnam> appel;
        try (MDC.MDCCloseable c = MDC.putCloseable(Correlation.CLE_MDC, "graphql-req-7")) {
            appel = CompletableFuture.supplyAsync(() -> client.consulterHistorique("PAT-0001"), executeur);
        }

        assertThat(appel.get().codeVignette()).isEqualTo("VG-2026-000001");
        assertThat(enteteRecu.get()).isEqualTo("graphql-req-7");
    }

    @Test
    void sansIdentifiant_pasDEntete() {
        client.consulterHistorique("PAT-0001");

        assertThat(enteteRecu.get()).isNull();
    }
}
