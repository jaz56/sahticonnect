package tn.sahticonnect.portail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Service REST portail-patient : profil, médecins, créneaux, rendez-vous, ordonnances, ambulances.
 * Contract-first : les interfaces des contrôleurs sont générées depuis docs/openapi/portail-patient.yaml.
 */
@SpringBootApplication
public class PortailPatientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortailPatientApplication.class, args);
    }
}
