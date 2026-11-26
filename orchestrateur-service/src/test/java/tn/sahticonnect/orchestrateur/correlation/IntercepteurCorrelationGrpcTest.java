package tn.sahticonnect.orchestrateur.correlation;

import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tn.sahticonnect.telemetrie.DernieresMesuresRequest;
import tn.sahticonnect.telemetrie.DernieresMesuresResponse;
import tn.sahticonnect.telemetrie.TelemetrieGrpc;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Intercepteur client gRPC : l'identifiant du thread appelant part dans la métadonnée x-correlation-id. */
class IntercepteurCorrelationGrpcTest {

    private final AtomicReference<Metadata> recues = new AtomicReference<>();
    private Server serveur;
    private ManagedChannel canalBrut;
    private Channel canal;

    @BeforeEach
    void demarrer() throws Exception {
        String nom = InProcessServerBuilder.generateName();
        TelemetrieGrpc.TelemetrieImplBase service = new TelemetrieGrpc.TelemetrieImplBase() {
            @Override
            public void dernieresMesures(DernieresMesuresRequest requete, StreamObserver<DernieresMesuresResponse> reponse) {
                reponse.onNext(DernieresMesuresResponse.getDefaultInstance());
                reponse.onCompleted();
            }
        };
        // côté serveur : on retient les métadonnées reçues
        ServerInterceptor capture = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> appel, Metadata entetes,
                                                              ServerCallHandler<Q, R> suivant) {
                recues.set(entetes);
                return suivant.startCall(appel, entetes);
            }
        };
        serveur = InProcessServerBuilder.forName(nom).directExecutor()
                .addService(ServerInterceptors.intercept(service, capture)).build().start();
        canalBrut = InProcessChannelBuilder.forName(nom).directExecutor().build();
        canal = ClientInterceptors.intercept(canalBrut, new IntercepteurCorrelationGrpc());
    }

    @AfterEach
    void arreter() {
        canalBrut.shutdownNow();
        serveur.shutdownNow();
    }

    private void appeler() {
        TelemetrieGrpc.newBlockingStub(canal)
                .dernieresMesures(DernieresMesuresRequest.newBuilder().setIdentifiantPatient("PAT-0001").build());
    }

    @Test
    void identifiantDuThread_envoyeDansLaMetadonnee() {
        try (MDC.MDCCloseable c = MDC.putCloseable(Correlation.CLE_MDC, "WF-2026-000049")) {
            appeler();
        }

        assertThat(recues.get().get(IntercepteurCorrelationGrpc.CLE)).isEqualTo("WF-2026-000049");
    }

    @Test
    void sansIdentifiant_pasDeMetadonnee() {
        appeler();

        assertThat(recues.get().containsKey(IntercepteurCorrelationGrpc.CLE)).isFalse();
    }
}
