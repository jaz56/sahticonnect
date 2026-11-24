package tn.sahticonnect.orchestrateur.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import tn.sahticonnect.orchestrateur.client.AbonnementAlertes;
import tn.sahticonnect.orchestrateur.service.OrchestrateurAlertes;

import java.time.Clock;

/**
 * Abonnement aux alertes au démarrage, via le canal Spring gRPC « telemetrie »
 * (désactivable : sahti.alertes.abonnement-actif=false, c'est le cas en test).
 */
@Configuration
public class AbonnementConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sahti.alertes", name = "abonnement-actif", havingValue = "true", matchIfMissing = true)
    public AbonnementAlertes abonnementAlertes(GrpcChannelFactory canaux, OrchestrateurAlertes orchestrateur,
                                               OrchestrateurProperties proprietes, Clock horloge) {
        return new AbonnementAlertes(canaux.createChannel("telemetrie"), orchestrateur::recevoir,
                proprietes.alertes().reconnexionMin(), proprietes.alertes().reconnexionMax(), horloge.getZone());
    }
}
