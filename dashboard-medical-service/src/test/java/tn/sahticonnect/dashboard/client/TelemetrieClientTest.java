package tn.sahticonnect.dashboard.client;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tn.sahticonnect.dashboard.client.ModelesDistants.ConstanteVitale;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.TypeConstante;
import tn.sahticonnect.telemetrie.DernieresMesuresRequest;
import tn.sahticonnect.telemetrie.DernieresMesuresResponse;
import tn.sahticonnect.telemetrie.Mesure;
import tn.sahticonnect.telemetrie.TelemetrieGrpc;
import tn.sahticonnect.telemetrie.TypeMesure;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Client gRPC contre un faux telemetrie-service in-process : le niveau vient de Mesure.niveau
 * (calculé par telemetrie-service), le dashboard ne calcule aucun seuil.
 */
class TelemetrieClientTest {

    private Server serveur;
    private ManagedChannel canal;

    @AfterEach
    void arreter() throws InterruptedException {
        canal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        serveur.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    /** Démarre un faux service qui exécute {@code comportement} pour chaque DernieresMesures. */
    private TelemetrieClient client(java.util.function.BiConsumer<DernieresMesuresRequest,
            StreamObserver<DernieresMesuresResponse>> comportement, Duration timeout) throws IOException {
        String nom = InProcessServerBuilder.generateName();
        serveur = InProcessServerBuilder.forName(nom).directExecutor().addService(new TelemetrieGrpc.TelemetrieImplBase() {
            @Override
            public void dernieresMesures(DernieresMesuresRequest requete, StreamObserver<DernieresMesuresResponse> reponse) {
                comportement.accept(requete, reponse);
            }
        }).build().start();
        canal = InProcessChannelBuilder.forName(nom).directExecutor().build();
        return new TelemetrieClient(canal, timeout, Clock.system(ZoneId.of("Africa/Tunis")));
    }

    private static Mesure mesure(TypeMesure type, double valeur, tn.sahticonnect.telemetrie.NiveauAlerte niveau, long sequence) {
        return Mesure.newBuilder().setIdentifiantPatient("PAT-0001").setType(type).setValeur(valeur).setUnite("bpm")
                .setHorodatage(Timestamp.newBuilder().setSeconds(1_795_000_000L + sequence)).setSequence(sequence)
                .setNiveau(niveau).build();
    }

    @Test
    void niveauRepriTelQuelDepuisTelemetrie() throws IOException {
        TelemetrieClient client = client((requete, reponse) -> {
            assertThat(requete.getNombre()).isEqualTo(3);
            assertThat(requete.getTypesList()).containsExactly(TypeMesure.FREQUENCE_CARDIAQUE);
            reponse.onNext(DernieresMesuresResponse.newBuilder().addAllMesures(List.of(
                    mesure(TypeMesure.FREQUENCE_CARDIAQUE, 142, tn.sahticonnect.telemetrie.NiveauAlerte.CRITIQUE, 3),
                    mesure(TypeMesure.FREQUENCE_CARDIAQUE, 115, tn.sahticonnect.telemetrie.NiveauAlerte.ELEVEE, 2),
                    mesure(TypeMesure.FREQUENCE_CARDIAQUE, 72, tn.sahticonnect.telemetrie.NiveauAlerte.NORMAL, 1)))
                    .build());
            reponse.onCompleted();
        }, Duration.ofSeconds(3));

        List<ConstanteVitale> constantes = client.dernieresMesures("PAT-0001", TypeConstante.FREQUENCE_CARDIAQUE, 3);

        assertThat(constantes).extracting(ConstanteVitale::valeur, ConstanteVitale::niveau).containsExactly(
                org.assertj.core.groups.Tuple.tuple(142.0, NiveauAlerte.CRITIQUE),
                org.assertj.core.groups.Tuple.tuple(115.0, NiveauAlerte.ELEVEE),
                org.assertj.core.groups.Tuple.tuple(72.0, null));
        assertThat(constantes.getFirst().type()).isEqualTo(TypeConstante.FREQUENCE_CARDIAQUE);
        assertThat(constantes.getFirst().horodatage().getOffset().getTotalSeconds()).isEqualTo(3600); // UTC+1
    }

    @Test
    void niveauNonRenseigne_null() throws IOException {
        // serveur antérieur au champ niveau : NIVEAU_NON_SPECIFIE → null, aucun calcul local
        TelemetrieClient client = client((requete, reponse) -> {
            reponse.onNext(DernieresMesuresResponse.newBuilder()
                    .addMesures(mesure(TypeMesure.FREQUENCE_CARDIAQUE, 150,
                            tn.sahticonnect.telemetrie.NiveauAlerte.NIVEAU_NON_SPECIFIE, 1)).build());
            reponse.onCompleted();
        }, Duration.ofSeconds(3));

        assertThat(client.dernieresMesures("PAT-0001", null, 10)).singleElement()
                .satisfies(c -> assertThat(c.niveau()).isNull());
    }

    @Test
    void patientNonSuivi_listeVide() throws IOException {
        TelemetrieClient client = client((requete, reponse) ->
                reponse.onError(Status.NOT_FOUND.withDescription("Patient inconnu").asRuntimeException()),
                Duration.ofSeconds(3));

        assertThat(client.dernieresMesures("PAT-0099", null, 10)).isEmpty();
    }

    @Test
    void serviceEnErreurOuTropLent_serviceIndisponible() throws IOException, InterruptedException {
        TelemetrieClient enPanne = client((requete, reponse) ->
                reponse.onError(Status.UNAVAILABLE.asRuntimeException()), Duration.ofSeconds(3));
        assertThatThrownBy(() -> enPanne.dernieresMesures("PAT-0001", null, 10))
                .isInstanceOf(ServiceIndisponibleException.class)
                .satisfies(e -> assertThat(((ServiceIndisponibleException) e).getService()).isEqualTo("telemetrie"));
        arreter();

        // ne répond jamais : la deadline coupe l'appel
        TelemetrieClient muet = client((requete, reponse) -> { }, Duration.ofMillis(200));
        assertThatThrownBy(() -> muet.dernieresMesures("PAT-0001", null, 10))
                .isInstanceOf(ServiceIndisponibleException.class)
                .hasMessageContaining("DEADLINE_EXCEEDED");
    }
}
