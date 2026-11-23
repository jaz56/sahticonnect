package tn.sahticonnect.dashboard.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationConfig {

    /** Horloge injectée (âge, traitements actifs, niveau de risque) : remplaçable en test. */
    @Bean
    public Clock horloge(SahtiProperties proprietes) {
        return Clock.system(ZoneId.of(proprietes.fuseauHoraire()));
    }

    /** Scalaires Date (2026-11-24) et DateTime (2026-11-24T09:30:00+01:00) du schéma. */
    @Bean
    public RuntimeWiringConfigurer scalaires() {
        return wiring -> wiring.scalar(ExtendedScalars.Date).scalar(ExtendedScalars.DateTime);
    }

    /**
     * Appels distants (gRPC, SOAP) lancés en parallèle : un thread virtuel par appel, pour qu'un champ
     * lent n'en bloque pas un autre dans la même requête GraphQL.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService executeurAppelsDistants() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
