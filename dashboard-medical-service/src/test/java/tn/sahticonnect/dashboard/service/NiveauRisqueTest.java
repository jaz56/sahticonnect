package tn.sahticonnect.dashboard.service;

import org.junit.jupiter.api.Test;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.NiveauRisque;
import tn.sahticonnect.dashboard.entity.StatutAlerte;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Règles du niveau de risque (24 h pour les alertes non résolues, 7 jours pour MODERE). */
class NiveauRisqueTest {

    private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-11-24T10:00:00+01:00");

    private static Alerte alerte(NiveauAlerte niveau, StatutAlerte statut, Duration ilYA) {
        Alerte a = new Alerte();
        a.setNiveau(niveau);
        a.setStatut(statut);
        a.setHorodatage(MAINTENANT.minus(ilYA));
        return a;
    }

    @Test
    void aucuneAlerte_faible() {
        assertThat(PatientService.calculer(List.of(), MAINTENANT)).isEqualTo(NiveauRisque.FAIBLE);
    }

    @Test
    void critiqueNonResolueDans24h_critique() {
        assertThat(PatientService.calculer(List.of(
                alerte(NiveauAlerte.ELEVEE, StatutAlerte.NOUVELLE, Duration.ofHours(1)),
                alerte(NiveauAlerte.CRITIQUE, StatutAlerte.PRISE_EN_CHARGE, Duration.ofHours(23))), MAINTENANT))
                .isEqualTo(NiveauRisque.CRITIQUE);
    }

    @Test
    void eleveeNonResolueDans24h_eleve() {
        assertThat(PatientService.calculer(List.of(
                alerte(NiveauAlerte.ELEVEE, StatutAlerte.NOUVELLE, Duration.ofHours(5))), MAINTENANT))
                .isEqualTo(NiveauRisque.ELEVE);
    }

    @Test
    void critiqueResolue_moderePasCritique() {
        assertThat(PatientService.calculer(List.of(
                alerte(NiveauAlerte.CRITIQUE, StatutAlerte.RESOLUE, Duration.ofHours(1))), MAINTENANT))
                .isEqualTo(NiveauRisque.MODERE);
    }

    @Test
    void critiqueDePlusDe24h_modere() {
        assertThat(PatientService.calculer(List.of(
                alerte(NiveauAlerte.CRITIQUE, StatutAlerte.NOUVELLE, Duration.ofHours(25))), MAINTENANT))
                .isEqualTo(NiveauRisque.MODERE);
    }
}
