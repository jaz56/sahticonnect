package tn.sahticonnect.telemetrie.correlation;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
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

/** Intercepteur serveur : métadonnée x-correlation-id → MDC pendant le traitement, renvoyée dans la réponse. */
class IntercepteurCorrelationServeurTest {

    private final AtomicReference<String> vuParLeService = new AtomicReference<>();
    private Server serveur;
    private ManagedChannel canal;

    @BeforeEach
    void demarrer() throws Exception {
        String nom = InProcessServerBuilder.generateName();
        // service minimal : retient la valeur du MDC vue pendant le traitement
        TelemetrieGrpc.TelemetrieImplBase service = new TelemetrieGrpc.TelemetrieImplBase() {
            @Override
            public void dernieresMesures(DernieresMesuresRequest requete, StreamObserver<DernieresMesuresResponse> reponse) {
                vuParLeService.set(MDC.get(Correlation.CLE_MDC));
                reponse.onNext(DernieresMesuresResponse.getDefaultInstance());
                reponse.onCompleted();
            }
        };
        serveur = InProcessServerBuilder.forName(nom).directExecutor()
                .addService(ServerInterceptors.intercept(service, new IntercepteurCorrelationServeur()))
                .build().start();
        canal = InProcessChannelBuilder.forName(nom).directExecutor().build();
    }

    @AfterEach
    void arreter() {
        canal.shutdownNow();
        serveur.shutdownNow();
    }

    /** Appel DernieresMesures avec (ou sans) métadonnée ; renvoie l'identifiant lu dans les en-têtes de réponse. */
    private String appeler(String correlation) {
        Metadata envoyees = new Metadata();
        if (correlation != null) {
            envoyees.put(IntercepteurCorrelationServeur.CLE, correlation);
        }
        AtomicReference<Metadata> recues = new AtomicReference<>();
        TelemetrieGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(envoyees),
                        MetadataUtils.newCaptureMetadataInterceptor(recues, new AtomicReference<>()))
                .dernieresMesures(DernieresMesuresRequest.newBuilder().setIdentifiantPatient("PAT-0001").build());
        return recues.get().get(IntercepteurCorrelationServeur.CLE);
    }

    @Test
    void metadonneeRecue_dansLeMdcEtRenvoyee() {
        String renvoye = appeler("WF-2026-000049");

        assertThat(vuParLeService.get()).isEqualTo("WF-2026-000049");
        assertThat(renvoye).isEqualTo("WF-2026-000049");
        assertThat(MDC.get(Correlation.CLE_MDC)).as("MDC rétabli après l'appel").isNull();
    }

    @Test
    void sansMetadonnee_identifiantGenere() {
        String renvoye = appeler(null);

        assertThat(renvoye).matches("[0-9a-f-]{36}").isEqualTo(vuParLeService.get());
    }
}
