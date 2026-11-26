package tn.sahticonnect.dashboard.correlation;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

/**
 * Appels gRPC sortants : ajoute la métadonnée {@code x-correlation-id} avec l'identifiant du traitement en cours
 * (lu au démarrage de l'appel, sur le thread appelant). {@link GlobalClientInterceptor} : Spring gRPC l'applique
 * à tous les canaux créés par GrpcChannelFactory.
 */
@Component
@GlobalClientInterceptor
public class IntercepteurCorrelationGrpc implements ClientInterceptor {

    public static final Metadata.Key<String> CLE = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> methode, CallOptions options, Channel canal) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(canal.newCall(methode, options)) {
            @Override
            public void start(Listener<R> ecouteur, Metadata entetes) {
                String id = Correlation.courant();
                if (id != null) {
                    entetes.put(CLE, id);
                }
                super.start(ecouteur, entetes);
            }
        };
    }
}
