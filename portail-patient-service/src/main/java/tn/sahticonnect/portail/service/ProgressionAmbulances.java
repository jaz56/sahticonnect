package tn.sahticonnect.portail.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Simulation du suivi des ambulances : toutes les 30 s, chaque mission avance d'une étape
 * (DEPECHEE → SUR_PLACE → TRANSPORT → TERMINEE).
 * Désactivable par la propriété portail.ambulances.simulation.active=false (c'est le cas en test).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "portail.ambulances.simulation", name = "active", havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ProgressionAmbulances {

    private final AmbulanceService ambulanceService;

    @Scheduled(fixedDelayString = "${portail.ambulances.simulation.intervalle-ms:30000}",
            initialDelayString = "${portail.ambulances.simulation.intervalle-ms:30000}")
    public void avancer() {
        int nombre = ambulanceService.faireProgresser();
        if (nombre > 0) {
            log.info("Simulation ambulances : {} mission(s) mise(s) à jour", nombre);
        }
    }
}
