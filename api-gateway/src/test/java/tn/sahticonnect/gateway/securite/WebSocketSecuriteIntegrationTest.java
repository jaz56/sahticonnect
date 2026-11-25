package tn.sahticonnect.gateway.securite;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.WebsocketServerSpec;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Subscription GraphQL (WebSocket) avec sécurité : le navigateur ne peut pas envoyer d'en-tête Authorization sur une
 * WebSocket, le jeton est donc accepté dans ?access_token pour l'upgrade de /api/graphql SEULEMENT, puis retiré
 * avant de relayer : le dashboard ne le reçoit jamais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "sahti.securite.active=true")
class WebSocketSecuriteIntegrationTest {

    private static final String PROTOCOLE = "graphql-transport-ws";
    /** URI et rôles reçus par le faux dashboard lors de l'upgrade relayé par la gateway. */
    private static final AtomicReference<String> uriRecue = new AtomicReference<>();
    private static final AtomicReference<String> rolesRecus = new AtomicReference<>();

    @RegisterExtension
    static WireMockExtension keycloak = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final DisposableServer fauxDashboard = HttpServer.create().port(0)
            .route(routes -> routes.get("/graphql", (requete, reponse) -> {
                uriRecue.set(requete.uri());
                rolesRecus.set(requete.requestHeaders().get("X-Roles"));
                return reponse.sendWebsocket((entree, sortie) -> sortie.sendString(
                        entree.receive().asString().map(m -> "{\"type\":\"connection_ack\"}")),
                        WebsocketServerSpec.builder().protocols(PROTOCOLE).build());
            }))
            .bindNow();

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) {
        registre.add("sahti.services.dashboard-medical", () -> "http://localhost:" + fauxDashboard.port());
        registre.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> keycloak.baseUrl() + Jetons.CHEMIN_JWKS);
        registre.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> Jetons.ISSUER_PUBLIC);
    }

    @AfterAll
    static void arreter() {
        fauxDashboard.disposeNow();
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void preparer() {
        keycloak.stubFor(get(Jetons.CHEMIN_JWKS).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(Jetons.jwks())));
        rolesRecus.set(null);
        uriRecue.set(null);
    }

    /** Ouvre la WebSocket via la gateway et attend le connection_ack. */
    private String connecter(String query, HttpHeaders entetes) {
        AtomicReference<String> recu = new AtomicReference<>();
        new ReactorNettyWebSocketClient().execute(URI.create("ws://localhost:" + port + "/api/graphql" + query), entetes,
                new WebSocketHandler() {
                    @Override
                    public List<String> getSubProtocols() {
                        return List.of(PROTOCOLE);
                    }

                    @Override
                    public Mono<Void> handle(WebSocketSession session) {
                        return session.send(Mono.just(session.textMessage("{\"type\":\"connection_init\"}")))
                                .and(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(recu::set));
                    }
                }).block(Duration.ofSeconds(10));
        return recu.get();
    }

    @Test
    void jetonMedecinDansLUrl_upgradeAccepte_etRolesTransmis() {
        String reponse = connecter("?access_token=" + Jetons.role("dr.chaabane", "MEDECIN"), new HttpHeaders());

        assertThat(reponse).contains("connection_ack");
        assertThat(rolesRecus.get()).isEqualTo("MEDECIN");
        assertThat(uriRecue.get()).as("le jeton ne doit jamais atteindre le service").isEqualTo("/graphql");
    }

    @Test
    void jetonRetireDeLUrlAvantDeRelayer() {
        // vérification directe du filtre : l'URL relayée ne contient plus access_token
        URI relayee = PropagationIdentiteFilter.sansJeton(URI.create(
                "http://localhost/api/graphql?access_token=eyJhbGciOi.xxx.yyy&autre=1"));
        assertThat(relayee.toString()).doesNotContain("access_token").contains("autre=1");
        assertThat(PropagationIdentiteFilter.sansJeton(URI.create("http://localhost/api/graphql?access_token=abc")).getRawQuery())
                .isNullOrEmpty();
    }

    @Test
    void sansJeton_upgradeRefuse401() {
        assertThatThrownBy(() -> connecter("", new HttpHeaders()))
                .hasMessageContaining("401");
    }

    @Test
    void jetonPatient_upgradeRefuse403() {
        assertThatThrownBy(() -> connecter("?access_token=" + Jetons.patient("ahmed", "PAT-0001"), new HttpHeaders()))
                .hasMessageContaining("403");
    }

    @Test
    void jetonInvalide_upgradeRefuse401() {
        assertThatThrownBy(() -> connecter("?access_token=" + Jetons.expire(), new HttpHeaders()))
                .hasMessageContaining("401");
    }
}
