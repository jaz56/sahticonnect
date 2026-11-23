package tn.sahticonnect.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Service GraphQL dashboard-medical : vue consolidée du médecin.
 * Données locales (patients, alertes, interventions, traitements) + champs résolus à la demande
 * auprès de telemetrie-service (gRPC) et dossier-cnam-service (SOAP).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DashboardMedicalApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardMedicalApplication.class, args);
    }
}
