package tn.sahticonnect.telemetrie.correlation;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Identifiant de corrélation des appels gRPC reçus (équivalent du filtre HTTP des autres services) :
 * <ul>
 *   <li>lu dans la métadonnée {@code x-correlation-id} (dashboard, orchestrateur, capteur), sinon généré ;</li>
 *   <li>mis dans le MDC pendant chaque étape de l'appel (début, messages, fin) : gRPC peut traiter les messages
 *       d'un même appel sur des threads différents, le MDC est donc posé à chaque fois ;</li>
 *   <li>renvoyé dans les en-têtes de réponse ;</li>
 *   <li>une ligne de journal par appel terminé (méthode, statut, durée) ; à l'ouverture aussi pour les flux.
 *       Le service de santé (grpc_health_probe du healthcheck Docker) n'est pas journalisé.</li>
 * </ul>
 * {@link GlobalServerInterceptor} : Spring gRPC l'applique à tous les services du serveur.
 */
@Component
@GlobalServerInterceptor
public class IntercepteurCorrelationServeur implements ServerInterceptor {

    public static final Metadata.Key<String> CLE = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Logger log = LoggerFactory.getLogger(IntercepteurCorrelationServeur.class);

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> appel, Metadata entetes,
                                                      ServerCallHandler<Q, R> suivant) {
        String id = Correlation.accepterOuGenerer(entetes.get(CLE));
        MethodDescriptor<Q, R> methode = appel.getMethodDescriptor();
        boolean journalise = !methode.getFullMethodName().startsWith("grpc.health");
        long debut = System.nanoTime();

        ServerCall<Q, R> appelTrace = new ForwardingServerCall.SimpleForwardingServerCall<>(appel) {
            @Override
            public void sendHeaders(Metadata entetesReponse) {
                entetesReponse.put(CLE, id);
                super.sendHeaders(entetesReponse);
            }

            @Override
            public void close(Status statut, Metadata fin) {
                dans(id, () -> {
                    if (journalise) {
                        log.info("gRPC {} → {} ({} ms)", methode.getFullMethodName(), statut.getCode(),
                                (System.nanoTime() - debut) / 1_000_000);
                    }
                    super.close(statut, fin);
                });
            }
        };

        ServerCall.Listener<Q> delegue = dansAvecResultat(id, () -> {
            if (journalise && methode.getType() != MethodDescriptor.MethodType.UNARY) {
                log.info("gRPC {} : flux ouvert", methode.getFullMethodName());
            }
            return suivant.startCall(appelTrace, entetes);
        });

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegue) {
            @Override
            public void onMessage(Q message) {
                dans(id, () -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                dans(id, super::onHalfClose);
            }

            @Override
            public void onCancel() {
                dans(id, super::onCancel);
            }

            @Override
            public void onComplete() {
                dans(id, super::onComplete);
            }

            @Override
            public void onReady() {
                dans(id, super::onReady);
            }
        };
    }

    /** Exécute l'action avec l'identifiant dans le MDC, puis rétablit la valeur précédente du thread. */
    private static void dans(String id, Runnable action) {
        dansAvecResultat(id, () -> {
            action.run();
            return null;
        });
    }

    private static <T> T dansAvecResultat(String id, java.util.function.Supplier<T> action) {
        String avant = MDC.get(Correlation.CLE_MDC);
        MDC.put(Correlation.CLE_MDC, id);
        try {
            return action.get();
        } finally {
            if (avant == null) {
                MDC.remove(Correlation.CLE_MDC);
            } else {
                MDC.put(Correlation.CLE_MDC, avant);
            }
        }
    }
}
