package tn.sahticonnect.orchestrateur.client;

import io.grpc.Channel;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.TypeMesure;
import tn.sahticonnect.orchestrateur.service.AlerteRecue;
import tn.sahticonnect.telemetrie.Alerte;
import tn.sahticonnect.telemetrie.SouscrireAlertesRequest;
import tn.sahticonnect.telemetrie.TelemetrieGrpc;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Abonnement gRPC à telemetrie-service (RPC server streaming SouscrireAlertes : tous les patients, niveau ≥ ELEVEE),
 * démarré avec l'application. Si le flux se coupe ou si telemetrie n'est pas démarré, reconnexion automatique
 * avec un backoff exponentiel (1 s, 2 s, 4 s… plafonné à 30 s), remis à 1 s dès qu'une alerte arrive.
 */
@Slf4j
public class AbonnementAlertes implements SmartLifecycle {

    private final TelemetrieGrpc.TelemetrieStub stub;
    private final Consumer<AlerteRecue> recepteur;
    private final Duration delaiMin;
    private final Duration delaiMax;
    private final ZoneId zone;
    private final ScheduledExecutorService planificateur = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "abonnement-alertes");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean actif;
    private volatile Duration delai;
    private volatile Context.CancellableContext contexte;
    private volatile int connexions;

    public AbonnementAlertes(Channel canal, Consumer<AlerteRecue> recepteur, Duration delaiMin, Duration delaiMax, ZoneId zone) {
        this.stub = TelemetrieGrpc.newStub(canal);
        this.recepteur = recepteur;
        this.delaiMin = delaiMin;
        this.delaiMax = delaiMax;
        this.zone = zone;
        this.delai = delaiMin;
    }

    @Override
    public void start() {
        actif = true;
        planificateur.execute(this::sAbonner);
    }

    @Override
    public void stop() {
        actif = false;
        Context.CancellableContext c = contexte;
        if (c != null) {
            c.cancel(null);
        }
        planificateur.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return actif;
    }

    /** Nombre de tentatives d'abonnement (tests, supervision). */
    public int connexions() {
        return connexions;
    }

    private void sAbonner() {
        if (!actif) {
            return;
        }
        connexions++;
        SouscrireAlertesRequest requete = SouscrireAlertesRequest.newBuilder()
                .setNiveauMinimum(tn.sahticonnect.telemetrie.NiveauAlerte.ELEVEE)
                .build();
        Context.CancellableContext nouveau = Context.current().withCancellation();
        contexte = nouveau;
        nouveau.run(() -> stub.souscrireAlertes(requete, new StreamObserver<>() {
            @Override
            public void onNext(Alerte alerte) {
                delai = delaiMin; // le flux fonctionne : backoff remis à zéro
                try {
                    recepteur.accept(versAlerteRecue(alerte));
                } catch (RuntimeException e) {
                    log.error("Alerte {} non prise en compte", alerte.getId(), e);
                }
            }

            @Override
            public void onError(Throwable erreur) {
                planifierReconnexion("flux interrompu : " + Status.fromThrowable(erreur).getCode());
            }

            @Override
            public void onCompleted() {
                planifierReconnexion("flux terminé par telemetrie-service");
            }
        }));
        log.info("Abonné aux alertes de telemetrie-service (tentative n°{})", connexions);
    }

    private void planifierReconnexion(String raison) {
        if (!actif) {
            return;
        }
        Duration attente = delai;
        delai = delai.multipliedBy(2).compareTo(delaiMax) > 0 ? delaiMax : delai.multipliedBy(2);
        log.warn("Abonnement aux alertes : {}, nouvelle tentative dans {} s", raison, attente.toMillis() / 1000.0);
        try {
            planificateur.schedule(this::sAbonner, attente.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException arret) {
            // application en cours d'arrêt
        }
    }

    AlerteRecue versAlerteRecue(Alerte alerte) {
        Instant instant = Instant.ofEpochSecond(alerte.getHorodatage().getSeconds(), alerte.getHorodatage().getNanos());
        return new AlerteRecue(alerte.getId(), alerte.getIdentifiantPatient(),
                NiveauAlerte.valueOf(alerte.getNiveau().name()), TypeMesure.valueOf(alerte.getTypeMesure().name()),
                alerte.getValeur(), alerte.getSeuilFranchi(), alerte.getMessage(), instant.atZone(zone).toOffsetDateTime());
    }
}
