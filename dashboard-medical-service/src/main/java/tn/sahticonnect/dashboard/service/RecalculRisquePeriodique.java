package tn.sahticonnect.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Recalcul périodique du niveau de risque (toutes les 5 min par défaut). Le niveau est recalculé à chaque
 * événement (alerte enregistrée, résolue, prise en charge), mais le temps qui passe n'est pas un événement :
 * sans cette tâche, un patient resterait CRITIQUE après les 24 h de son alerte.
 * Propriétés : sahti.risque.recalcul.actif (désactivé pendant les tests), sahti.risque.recalcul.intervalle-ms.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sahti.risque.recalcul", name = "actif", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RecalculRisquePeriodique {

    private final PatientService patientService;

    @Scheduled(fixedDelayString = "${sahti.risque.recalcul.intervalle-ms:300000}",
            initialDelayString = "${sahti.risque.recalcul.intervalle-ms:300000}")
    public void recalculer() {
        int changements = patientService.recalculerTous();
        if (changements > 0) {
            log.info("Recalcul périodique du niveau de risque : {} patient(s) mis à jour", changements);
        }
    }
}
