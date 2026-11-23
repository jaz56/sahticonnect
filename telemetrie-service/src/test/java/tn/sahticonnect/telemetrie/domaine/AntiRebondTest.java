package tn.sahticonnect.telemetrie.domaine;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static tn.sahticonnect.telemetrie.domaine.Constante.FREQUENCE_CARDIAQUE;
import static tn.sahticonnect.telemetrie.domaine.Constante.SPO2;
import static tn.sahticonnect.telemetrie.domaine.Niveau.CRITIQUE;
import static tn.sahticonnect.telemetrie.domaine.Niveau.ELEVEE;
import static tn.sahticonnect.telemetrie.domaine.Niveau.NORMAL;

/** Anti-rebond avec une horloge fixe que le test fait avancer à la main. */
class AntiRebondTest {

    /** Horloge figée, avancée explicitement par le test. */
    static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-11-24T09:00:00Z");

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public Instant instant() {
            return maintenant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Africa/Tunis");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final HorlogeReglable horloge = new HorlogeReglable();
    private final AntiRebond antiRebond = new AntiRebond(horloge, Duration.ofSeconds(60));

    @Test
    void mesureNormale_jamaisDAlerte() {
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, NORMAL)).isFalse();
    }

    @Test
    void repetitionDansLaFenetre_filtree() {
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isTrue();
        horloge.avancer(Duration.ofSeconds(1));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isFalse();
        horloge.avancer(Duration.ofSeconds(58));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isFalse();
    }

    @Test
    void fenetreEcoulee_nouvelleAlerte() {
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, CRITIQUE)).isTrue();
        horloge.avancer(Duration.ofSeconds(59));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, CRITIQUE)).isFalse();
        horloge.avancer(Duration.ofSeconds(1)); // 60 s après la première alerte
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, CRITIQUE)).isTrue();
        // la fenêtre repart de la dernière alerte émise
        horloge.avancer(Duration.ofSeconds(30));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, CRITIQUE)).isFalse();
    }

    @Test
    void aggravation_emiseImmediatement() {
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isTrue();
        horloge.avancer(Duration.ofSeconds(2));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, CRITIQUE)).isTrue();
        horloge.avancer(Duration.ofSeconds(2));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, CRITIQUE)).isFalse();
    }

    @Test
    void amelioration_pasDeNouvelleAlerteTantQueLeNiveauSuperieurEstRecent() {
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, CRITIQUE)).isTrue();
        horloge.avancer(Duration.ofSeconds(5));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isFalse();
        horloge.avancer(Duration.ofSeconds(60));
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isTrue();
    }

    @Test
    void patientsEtTypesIndependants() {
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isTrue();
        assertThat(antiRebond.autoriser("PAT-0002", FREQUENCE_CARDIAQUE, ELEVEE)).isTrue();
        assertThat(antiRebond.autoriser("PAT-0001", SPO2, ELEVEE)).isTrue();
    }

    @Test
    void reinitialiser_oublieLesAlertes() {
        antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE);
        antiRebond.reinitialiser();
        assertThat(antiRebond.autoriser("PAT-0001", FREQUENCE_CARDIAQUE, ELEVEE)).isTrue();
    }
}
