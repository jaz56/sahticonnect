package tn.sahticonnect.orchestrateur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Orchestrateur SahtiConnect : reçoit les alertes de telemetrie-service (gRPC) et pilote le workflow
 * de prise en charge à domicile en appelant le dossier CNAM (SOAP), le portail patient (REST) et le
 * dashboard médical (GraphQL).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrchestrateurApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestrateurApplication.class, args);
    }
}
