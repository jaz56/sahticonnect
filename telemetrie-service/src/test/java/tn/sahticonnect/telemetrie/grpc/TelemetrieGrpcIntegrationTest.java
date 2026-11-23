package tn.sahticonnect.telemetrie.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.sahticonnect.telemetrie.AccuseReception;
import tn.sahticonnect.telemetrie.Alerte;
import tn.sahticonnect.telemetrie.DernieresMesuresRequest;
import tn.sahticonnect.telemetrie.DernieresMesuresResponse;
import tn.sahticonnect.telemetrie.Mesure;
import tn.sahticonnect.telemetrie.NiveauAlerte;
import tn.sahticonnect.telemetrie.SouscrireAlertesRequest;
import tn.sahticonnect.telemetrie.StatutReception;
import tn.sahticonnect.telemetrie.TelemetrieGrpc;
import tn.sahticonnect.telemetrie.TypeMesure;
import tn.sahticonnect.telemetrie.domaine.AntiRebond;
import tn.sahticonnect.telemetrie.repository.AlerteRepository;
import tn.sahticonnect.telemetrie.service.DiffuseurAlertes;
import tn.sahticonnect.telemetrie.service.StockageMesures;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tn.sahticonnect.telemetrie.TypeMesure.FREQUENCE_CARDIAQUE;
import static tn.sahticonnect.telemetrie.TypeMesure.SPO2;

/**
 * Tests d'intégration : contexte Spring complet, serveur gRPC in-process (nommé « telemetrie-test »,
 * voir src/test/resources/application.properties), vrais stubs générés depuis le .proto.
 */
@SpringBootTest
class TelemetrieGrpcIntegrationTest {

    @Autowired
    private StockageMesures stockage;
    @Autowired
    private AntiRebond antiRebond;
    @Autowired
    private DiffuseurAlertes diffuseur;
    @Autowired
    private AlerteRepository alerteRepository;

    private ManagedChannel canal;
    private TelemetrieGrpc.TelemetrieStub stubAsynchrone;
    private TelemetrieGrpc.TelemetrieBlockingStub stubBloquant;

    @BeforeEach
    void preparer() {
        stockage.vider();
        antiRebond.reinitialiser();
        canal = InProcessChannelBuilder.forName("telemetrie-test").build();
        stubAsynchrone = TelemetrieGrpc.newStub(canal);
        stubBloquant = TelemetrieGrpc.newBlockingStub(canal);
    }

    @AfterEach
    void fermer() throws InterruptedException {
        canal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ===================== Utilitaires =====================

    private static long sequence;

    private static Mesure mesure(String patient, TypeMesure type, double valeur) {
        return Mesure.newBuilder().setIdentifiantPatient(patient).setCapteurId("CAP-TEST").setType(type)
                .setValeur(valeur).setSequence(++sequence).build();
    }

    /** Envoie les mesures sur un flux EnvoyerMesures, ferme le flux et renvoie les accusés. */
    private List<AccuseReception> envoyer(Mesure... mesures) throws InterruptedException {
        List<AccuseReception> accuses = new CopyOnWriteArrayList<>();
        CountDownLatch fin = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        StreamObserver<Mesure> envoi = stubAsynchrone.envoyerMesures(new StreamObserver<>() {
            @Override
            public void onNext(AccuseReception accuse) {
                accuses.add(accuse);
            }

            @Override
            public void onError(Throwable t) {
                erreur.set(t);
                fin.countDown();
            }

            @Override
            public void onCompleted() {
                fin.countDown();
            }
        });
        for (Mesure m : mesures) {
            envoi.onNext(m);
        }
        envoi.onCompleted();
        assertThat(fin.await(10, TimeUnit.SECONDS)).as("le serveur termine le flux").isTrue();
        assertThat(erreur.get()).as("aucune erreur gRPC").isNull();
        return accuses;
    }

    /** Abonné de test : reçoit les alertes et peut annuler son abonnement comme un vrai client. */
    private static final class Abonne implements ClientResponseObserver<SouscrireAlertesRequest, Alerte> {
        final List<Alerte> alertes = new CopyOnWriteArrayList<>();
        private ClientCallStreamObserver<SouscrireAlertesRequest> appel;

        @Override
        public void beforeStart(ClientCallStreamObserver<SouscrireAlertesRequest> appel) {
            this.appel = appel;
        }

        @Override
        public void onNext(Alerte alerte) {
            alertes.add(alerte);
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
        }

        void annuler() {
            appel.cancel("fin du test", null);
        }
    }

    private static void attendre(BooleanSupplier condition, String description) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("Délai dépassé : " + description);
            }
            Thread.sleep(20);
        }
    }

    // ===================== EnvoyerMesures =====================

    @Test
    void envoyerMesures_unAccuseParMesure_alerteEmisePuisFiltree() throws Exception {
        Mesure normale = mesure("PAT-0001", FREQUENCE_CARDIAQUE, 72);
        Mesure critique = mesure("PAT-0001", FREQUENCE_CARDIAQUE, 142);
        Mesure repetee = mesure("PAT-0001", FREQUENCE_CARDIAQUE, 145);

        List<AccuseReception> accuses = envoyer(normale, critique, repetee);

        assertThat(accuses).extracting(AccuseReception::getSequence)
                .containsExactly(normale.getSequence(), critique.getSequence(), repetee.getSequence());
        assertThat(accuses).allMatch(a -> a.getStatut() == StatutReception.ACCEPTEE);

        assertThat(accuses.get(0).getNiveau()).isEqualTo(NiveauAlerte.NORMAL);
        assertThat(accuses.get(0).hasAlerte()).isFalse();

        AccuseReception avecAlerte = accuses.get(1);
        assertThat(avecAlerte.getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(avecAlerte.hasAlerte()).isTrue();
        Alerte alerte = avecAlerte.getAlerte();
        assertThat(alerte.getId()).matches("ALR-\\d{4}-\\d{6}");
        assertThat(alerte.getSeuilFranchi()).isEqualTo("FC > 130");
        assertThat(alerte.getMessage()).isEqualTo("Alerte CRITIQUE : fréquence cardiaque 142 bpm (FC > 130)");
        assertThat(alerte.getMesure().getSequence()).isEqualTo(critique.getSequence());
        assertThat(alerte.hasHorodatage()).isTrue();

        // anti-rebond : mesure acceptée, niveau indiqué, mais pas de nouvelle alerte
        assertThat(accuses.get(2).getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(accuses.get(2).hasAlerte()).isFalse();
        assertThat(accuses.get(2).getMessage()).contains("alerte déjà émise");

        // l'alerte est persistée
        assertThat(alerteRepository.findById(alerte.getId())).hasValueSatisfying(a -> {
            assertThat(a.getIdentifiantPatient()).isEqualTo("PAT-0001");
            assertThat(a.getValeur()).isEqualTo(142);
        });
    }

    @Test
    void envoyerMesures_aggravationEmiseImmediatement() throws Exception {
        List<AccuseReception> accuses = envoyer(
                mesure("PAT-0003", SPO2, 93),
                mesure("PAT-0003", SPO2, 92),
                mesure("PAT-0003", SPO2, 87));

        assertThat(accuses.get(0).getAlerte().getNiveau()).isEqualTo(NiveauAlerte.ELEVEE);
        assertThat(accuses.get(1).hasAlerte()).isFalse();
        assertThat(accuses.get(2).getAlerte().getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(accuses.get(2).getAlerte().getSeuilFranchi()).isEqualTo("SpO2 < 90");
    }

    @Test
    void envoyerMesures_rejetsSansCouperLeFlux() throws Exception {
        List<AccuseReception> accuses = envoyer(
                mesure("PAT-0099", FREQUENCE_CARDIAQUE, 72),
                mesure("PAT-0001", TypeMesure.TYPE_MESURE_NON_SPECIFIE, 72),
                mesure("PAT-0001", FREQUENCE_CARDIAQUE, 350),
                mesure("PAT-0001", TypeMesure.TEMPERATURE, 12),
                mesure("PAT-0001", TypeMesure.CHUTE, 2),
                mesure("PAT-0001", FREQUENCE_CARDIAQUE, 75));

        assertThat(accuses).hasSize(6);
        assertThat(accuses.subList(0, 5)).allMatch(a -> a.getStatut() == StatutReception.REJETEE && !a.hasAlerte());
        assertThat(accuses.get(0).getMessage()).isEqualTo("Patient inconnu : PAT-0099");
        assertThat(accuses.get(1).getMessage()).isEqualTo("Type de mesure non spécifié");
        assertThat(accuses.get(2).getMessage()).contains("hors plage physique", "350", "0 à 300 bpm");
        assertThat(accuses.get(3).getMessage()).contains("température", "25 à 45 °C");
        assertThat(accuses.get(4).getMessage()).contains("0 ou 1");
        assertThat(accuses.get(5).getStatut()).isEqualTo(StatutReception.ACCEPTEE);
        // les mesures rejetées ne sont pas stockées
        assertThat(stubBloquant.dernieresMesures(DernieresMesuresRequest.newBuilder()
                .setIdentifiantPatient("PAT-0001").build()).getMesuresList())
                .extracting(Mesure::getValeur).containsExactly(75.0);
    }

    // ===================== SouscrireAlertes =====================

    @Test
    void souscrireAlertes_filtreParPatientEtNiveau_puisNettoyageALaDeconnexion() throws Exception {
        int avant = diffuseur.nombreAbonnes();
        Abonne critiquesDeLeila = new Abonne();
        Abonne tout = new Abonne();
        stubAsynchrone.souscrireAlertes(SouscrireAlertesRequest.newBuilder()
                .addIdentifiantsPatients("PAT-0002").setNiveauMinimum(NiveauAlerte.CRITIQUE).build(), critiquesDeLeila);
        stubAsynchrone.souscrireAlertes(SouscrireAlertesRequest.getDefaultInstance(), tout); // NON_SPECIFIE = ELEVEE
        attendre(() -> diffuseur.nombreAbonnes() == avant + 2, "2 abonnés enregistrés");

        envoyer(mesure("PAT-0002", SPO2, 92),                  // ELEVEE PAT-0002
                mesure("PAT-0004", FREQUENCE_CARDIAQUE, 150),  // CRITIQUE PAT-0004
                mesure("PAT-0002", SPO2, 85));                 // CRITIQUE PAT-0002

        attendre(() -> tout.alertes.size() == 3, "3 alertes pour l'abonné sans filtre");
        assertThat(tout.alertes).extracting(Alerte::getIdentifiantPatient)
                .containsExactly("PAT-0002", "PAT-0004", "PAT-0002");
        attendre(() -> critiquesDeLeila.alertes.size() == 1, "1 alerte pour l'abonné filtré");
        assertThat(critiquesDeLeila.alertes.getFirst().getNiveau()).isEqualTo(NiveauAlerte.CRITIQUE);
        assertThat(critiquesDeLeila.alertes.getFirst().getIdentifiantPatient()).isEqualTo("PAT-0002");

        // déconnexion du client → abonnement retiré côté serveur (setOnCancelHandler)
        critiquesDeLeila.annuler();
        attendre(() -> diffuseur.nombreAbonnes() == avant + 1, "abonné annulé retiré");
        tout.annuler();
        attendre(() -> diffuseur.nombreAbonnes() == avant, "tous les abonnés retirés");
    }

    @Test
    void souscrireAlertes_identifiantMalForme_invalidArgument() {
        var flux = stubBloquant.souscrireAlertes(SouscrireAlertesRequest.newBuilder()
                .addIdentifiantsPatients("P1").build());
        assertThatThrownBy(flux::hasNext)
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(Status.fromThrowable(e).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    // ===================== DernieresMesures =====================

    @Test
    void dernieresMesures_plusRecenteEnPremier_10ParDefaut_filtreParType() throws Exception {
        List<Mesure> mesures = new ArrayList<>();
        Instant debut = Instant.parse("2026-11-24T08:00:00Z");
        for (int i = 0; i < 15; i++) {
            mesures.add(mesure("PAT-0005", FREQUENCE_CARDIAQUE, 70 + i).toBuilder()
                    .setHorodatage(ConvertisseurProto.versTimestamp(debut.plusSeconds(i))).build());
        }
        mesures.add(mesure("PAT-0005", SPO2, 97).toBuilder()
                .setHorodatage(ConvertisseurProto.versTimestamp(debut.plusSeconds(100))).build());
        envoyer(mesures.toArray(Mesure[]::new));

        DernieresMesuresResponse parDefaut = stubBloquant.dernieresMesures(DernieresMesuresRequest.newBuilder()
                .setIdentifiantPatient("PAT-0005").addTypes(FREQUENCE_CARDIAQUE).build());
        assertThat(parDefaut.getIdentifiantPatient()).isEqualTo("PAT-0005");
        assertThat(parDefaut.getMesuresList()).hasSize(10)
                .extracting(Mesure::getValeur).startsWith(84.0, 83.0).endsWith(75.0);
        assertThat(parDefaut.getMesures(0).getUnite()).isEqualTo("bpm");

        DernieresMesuresResponse tousTypes = stubBloquant.dernieresMesures(DernieresMesuresRequest.newBuilder()
                .setIdentifiantPatient("PAT-0005").setNombre(2).build());
        assertThat(tousTypes.getMesuresList()).extracting(Mesure::getType)
                .containsExactly(SPO2, FREQUENCE_CARDIAQUE, FREQUENCE_CARDIAQUE);
    }

    @Test
    void dernieresMesures_patientSansMesure_listeVide() {
        assertThat(stubBloquant.dernieresMesures(DernieresMesuresRequest.newBuilder()
                .setIdentifiantPatient("PAT-0004").build()).getMesuresList()).isEmpty();
    }

    @Test
    void dernieresMesures_patientInconnu_notFound() {
        assertThatThrownBy(() -> stubBloquant.dernieresMesures(DernieresMesuresRequest.newBuilder()
                .setIdentifiantPatient("PAT-0099").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> {
                    Status status = Status.fromThrowable(e);
                    assertThat(status.getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(status.getDescription()).isEqualTo("Patient inconnu : PAT-0099");
                });
    }

    @Test
    void dernieresMesures_requeteInvalide_invalidArgument() {
        for (DernieresMesuresRequest invalide : List.of(
                DernieresMesuresRequest.newBuilder().setIdentifiantPatient("X1").build(),
                DernieresMesuresRequest.getDefaultInstance(),
                DernieresMesuresRequest.newBuilder().setIdentifiantPatient("PAT-0001").setNombre(-1).build(),
                DernieresMesuresRequest.newBuilder().setIdentifiantPatient("PAT-0001").setNombre(101).build(),
                DernieresMesuresRequest.newBuilder().setIdentifiantPatient("PAT-0001")
                        .addTypes(TypeMesure.TYPE_MESURE_NON_SPECIFIE).build())) {
            assertThatThrownBy(() -> stubBloquant.dernieresMesures(invalide))
                    .as(invalide.toString())
                    .satisfies(e -> assertThat(Status.fromThrowable(e).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        }
    }

    // ===================== Réflexion et santé =====================

    @Test
    void healthCheck_serving() {
        HealthCheckResponse reponse = HealthGrpc.newBlockingStub(canal).check(HealthCheckRequest.getDefaultInstance());
        assertThat(reponse.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }

    @Test
    void reflection_listeLesServices() throws Exception {
        List<String> services = new CopyOnWriteArrayList<>();
        CountDownLatch fin = new CountDownLatch(1);
        StreamObserver<ServerReflectionRequest> requetes = ServerReflectionGrpc.newStub(canal)
                .serverReflectionInfo(new StreamObserver<>() {
                    @Override
                    public void onNext(ServerReflectionResponse reponse) {
                        reponse.getListServicesResponse().getServiceList().stream()
                                .map(ServiceResponse::getName).forEach(services::add);
                    }

                    @Override
                    public void onError(Throwable t) {
                        fin.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        fin.countDown();
                    }
                });
        requetes.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
        requetes.onCompleted();
        assertThat(fin.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(services).contains("sahticonnect.telemetrie.Telemetrie", "grpc.health.v1.Health");
    }
}
