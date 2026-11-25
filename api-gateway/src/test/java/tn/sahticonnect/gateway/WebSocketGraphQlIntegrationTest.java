package tn.sahticonnect.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.WebsocketServerSpec;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Subscription GraphQL à travers la gateway : ws://gateway/api/graphql → ws://dashboard/graphql.
 * Le dashboard est remplacé par un petit serveur WebSocket (Reactor Netty) qui parle graphql-transport-ws :
 * connection_init → connection_ack, subscribe → next.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sahti.securite.active=false") // routage seul : la sécurité est testée dans securite/
class WebSocketGraphQlIntegrationTest {

    private static final String PROTOCOLE = "graphql-transport-ws";
    private static final AtomicReference<String> correlationRecue = new AtomicReference<>();

    private static final DisposableServer faux_dashboard = HttpServer.create().port(0)
            .route(routes -> routes.ws("/graphql", (entree, sortie) -> {
                correlationRecue.set(entree.headers().get("X-Correlation-Id"));
                return sortie.sendString(entree.receive().asString().map(message -> message.contains("connection_init")
                        ? "{\"type\":\"connection_ack\"}"
                        : "{\"id\":\"1\",\"type\":\"next\",\"payload\":{\"data\":{\"nouvelleAlerte\":"
                        + "{\"id\":\"ALR-2026-000001\",\"niveau\":\"CRITIQUE\"}}}}"));
            }, WebsocketServerSpec.builder().protocols(PROTOCOLE).build()))
            .bindNow();

    @DynamicPropertySource
    static void dashboard(DynamicPropertyRegistry registre) {
        registre.add("sahti.services.dashboard-medical", () -> "http://localhost:" + faux_dashboard.port());
    }

    @AfterAll
    static void arreter() {
        faux_dashboard.disposeNow();
    }

    @LocalServerPort
    private int port;

    @Test
    void subscription_relayeeEnWebSocket() {
        List<String> recus = new CopyOnWriteArrayList<>();
        AtomicReference<String> protocoleNegocie = new AtomicReference<>();
        HttpHeaders entetes = new HttpHeaders();
        entetes.set("X-Correlation-Id", "ws-demo-1");

        new ReactorNettyWebSocketClient().execute(URI.create("ws://localhost:" + port + "/api/graphql"), entetes,
                new WebSocketHandler() {
                    @Override
                    public List<String> getSubProtocols() {
                        return List.of(PROTOCOLE);
                    }

                    @Override
                    public Mono<Void> handle(WebSocketSession session) {
                        protocoleNegocie.set(session.getHandshakeInfo().getSubProtocol());
                        Flux<WebSocketMessage> envois = Flux.just(
                                "{\"type\":\"connection_init\"}",
                                "{\"id\":\"1\",\"type\":\"subscribe\",\"payload\":{\"query\":"
                                        + "\"subscription { nouvelleAlerte { id niveau } }\"}}")
                                .map(session::textMessage);
                        return session.send(envois)
                                .and(session.receive().map(WebSocketMessage::getPayloadAsText).take(2).doOnNext(recus::add));
                    }
                }).block(Duration.ofSeconds(10));

        assertThat(protocoleNegocie.get()).isEqualTo(PROTOCOLE);
        assertThat(recus).hasSize(2);
        assertThat(recus.get(0)).contains("connection_ack");
        assertThat(recus.get(1)).contains("\"type\":\"next\"", "ALR-2026-000001");
        assertThat(correlationRecue.get()).as("X-Correlation-Id transmis au dashboard").isEqualTo("ws-demo-1");
    }
}
