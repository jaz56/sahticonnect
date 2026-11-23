package tn.sahticonnect.dashboard.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;
import org.springframework.grpc.client.GrpcChannelFactory;
import tn.sahticonnect.dashboard.client.ModelesDistants.ConstanteVitale;
import tn.sahticonnect.dashboard.config.SahtiProperties;
import tn.sahticonnect.dashboard.entity.TypeConstante;
import tn.sahticonnect.telemetrie.DernieresMesuresRequest;
import tn.sahticonnect.telemetrie.Mesure;
import tn.sahticonnect.telemetrie.TelemetrieGrpc;
import tn.sahticonnect.telemetrie.TypeMesure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client gRPC de telemetrie-service (RPC unaire DernieresMesures), via le canal Spring gRPC « telemetrie ».
 * Chaque appel a une deadline (3 s) : au-delà, ou si le service est injoignable → ServiceIndisponibleException.
 */
@Component
public class TelemetrieClient {

    private final TelemetrieGrpc.TelemetrieBlockingStub stub;
    private final Duration timeout;
    private final Clock horloge;

    public TelemetrieClient(GrpcChannelFactory canaux, SahtiProperties proprietes, Clock horloge) {
        this.stub = TelemetrieGrpc.newBlockingStub(canaux.createChannel("telemetrie"));
        this.timeout = proprietes.timeout();
        this.horloge = horloge;
    }

    /**
     * Dernières mesures, la plus récente en premier.
     *
     * @param type   constante voulue, ou null pour toutes
     * @param nombre nombre de mesures par type (1 à 100)
     */
    public List<ConstanteVitale> dernieresMesures(String patientId, TypeConstante type, int nombre) {
        DernieresMesuresRequest.Builder requete = DernieresMesuresRequest.newBuilder()
                .setIdentifiantPatient(patientId)
                .setNombre(nombre);
        if (type != null) {
            requete.addTypes(TypeMesure.valueOf(type.name()));
        }
        try {
            return stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .dernieresMesures(requete.build())
                    .getMesuresList().stream()
                    .map(this::versConstante)
                    .toList();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return List.of(); // patient non suivi par la télémétrie : aucune mesure
            }
            throw new ServiceIndisponibleException(ServiceIndisponibleException.TELEMETRIE,
                    e.getStatus().getCode() + (e.getStatus().getDescription() == null ? ""
                            : " (" + e.getStatus().getDescription() + ")"), e);
        }
    }

    private ConstanteVitale versConstante(Mesure mesure) {
        TypeConstante type = TypeConstante.valueOf(mesure.getType().name());
        Instant instant = Instant.ofEpochSecond(mesure.getHorodatage().getSeconds(), mesure.getHorodatage().getNanos());
        String id = mesure.getIdentifiantPatient() + "-" + type + "-" + instant.toEpochMilli() + "-" + mesure.getSequence();
        return new ConstanteVitale(id, mesure.getIdentifiantPatient(), type, mesure.getValeur(), mesure.getUnite(),
                instant.atZone(horloge.getZone()).toOffsetDateTime(), NiveauMesure.de(type, mesure.getValeur()));
    }
}
