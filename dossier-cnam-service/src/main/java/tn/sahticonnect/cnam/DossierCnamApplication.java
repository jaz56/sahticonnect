package tn.sahticonnect.cnam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Service SOAP dossier-cnam : droits CNAM, feuilles de soins, historique, comparaison de garanties.
 */
@SpringBootApplication
public class DossierCnamApplication {

    public static void main(String[] args) {
        SpringApplication.run(DossierCnamApplication.class, args);
    }

    /** Horloge injectable (date de l'acte par défaut = aujourd'hui). */
    @Bean
    public Clock horloge() {
        return Clock.systemDefaultZone();
    }
}
