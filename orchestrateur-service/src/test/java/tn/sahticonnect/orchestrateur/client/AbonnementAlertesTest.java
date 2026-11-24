package tn.sahticonnect.orchestrateur.client;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.TypeMesure;
import tn.sahticonnect.orchestrateur.service.AlerteRecue;
import tn.sahticonnect.telemetrie.Alerte;
import tn.sahticonnect.telemetrie.SouscrireAlertesRequest;
import tn.sahticonnect.telemetrie.TelemetrieGrpc;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Abonnement aux alertes contre un faux telemetrie-service in-process : réception, conversion, reconnexion
 * automatique quand le flux se coupe.
 */
class AbonnementAlertesTest {

    private Server serveur;
    private ManagedChannel canal;
    private AbonnementAlertes abonnement;
    private final List<SouscrireAlertesRequest> requetes = new CopyOnWriteArrayList<>();
    private final AtomicInteger souscriptions = new AtomicInteger();

    @AfterEach
    void arreter() throws InterruptedException {
        abonnement.stop();
        canal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        serveur.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static Alerte alerte(String id, String patient) {
        return Alerte.newBuilder().setId(id).setIdentifiantPatient(patient)
                .setNiveau(tn.sahticonnect.telemetrie.NiveauAlerte.CRITIQUE)
                .setTypeMesure(tn.sahticonnect.telemetrie.TypeMesure.FREQUENCE_CARDIAQUE).setValeur(142)
                .setSeuilFranchi("FC > 130").setMessage("Alerte CRITIQUE : fréquence cardiaque 142 bpm (FC > 130)")
                .setHorodatage(Timestamp.newBuilder().setSeconds(1_795_000_000L)).build();
    }

    @Test
    void recoitLesAlertes_etSeReconnecteQuandLeFluxSeCoupe() throws Exception {
        String nom = InProcessServerBuilder.generateName();
        serveur = InProcessServerBuilder.forName(nom).directExecutor().addService(new TelemetrieGrpc.TelemetrieImplBase() {
            @Override
            public void souscrireAlertes(SouscrireAlertesRequest requete, StreamObserver<Alerte> flux) {
                requetes.add(requete);
                if (souscriptions.incrementAndGet() == 1) {
                    flux.onNext(alerte("ALR-2026-000001", "PAT-0001"));
                    flux.onError(Status.UNAVAILABLE.withDescription("telemetrie redémarre").asRuntimeException());
                } else {
                    flux.onNext(alerte("ALR-2026-000002", "PAT-0003")); // flux laissé ouvert
                }
            }
        }).build().start();
        canal = InProcessChannelBuilder.forName(nom).build();
        List<AlerteRecue> recues = new CopyOnWriteArrayList<>();
        abonnement = new AbonnementAlertes(canal, recues::add, Duration.ofMillis(50), Duration.ofMillis(200),
                ZoneId.of("Africa/Tunis"));

        abonnement.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> recues.size() == 2);
        assertThat(souscriptions.get()).isEqualTo(2);
        assertThat(requetes).allSatisfy(r -> {
            assertThat(r.getIdentifiantsPatientsList()).isEmpty(); // tous les patients
            assertThat(r.getNiveauMinimum()).isEqualTo(tn.sahticonnect.telemetrie.NiveauAlerte.ELEVEE);
        });
        assertThat(recues.getFirst()).satisfies(a -> {
            assertThat(a.alerteId()).isEqualTo("ALR-2026-000001");
            assertThat(a.niveau()).isEqualTo(NiveauAlerte.CRITIQUE);
            assertThat(a.typeMesure()).isEqualTo(TypeMesure.FREQUENCE_CARDIAQUE);
            assertThat(a.seuilFranchi()).isEqualTo("FC > 130");
            assertThat(a.horodatage().getOffset().getTotalSeconds()).isEqualTo(3600);
        });
        assertThat(recues.get(1).patientId()).isEqualTo("PAT-0003");
    }

    @Test
    void telemetrieAbsente_reessaieAvecBackoff() throws Exception {
        // aucun serveur sous ce nom : chaque tentative échoue (UNAVAILABLE)
        serveur = InProcessServerBuilder.forName(InProcessServerBuilder.generateName()).build().start();
        canal = InProcessChannelBuilder.forName("telemetrie-absent").build();
        abonnement = new AbonnementAlertes(canal, a -> { }, Duration.ofMillis(20), Duration.ofMillis(80),
                ZoneId.of("Africa/Tunis"));

        abonnement.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> abonnement.connexions() >= 4);
        assertThat(abonnement.isRunning()).isTrue();
    }
}
