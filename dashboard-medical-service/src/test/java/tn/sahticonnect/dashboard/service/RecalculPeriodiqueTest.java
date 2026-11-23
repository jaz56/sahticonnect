package tn.sahticonnect.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.NiveauRisque;
import tn.sahticonnect.dashboard.entity.PatientSuivi;
import tn.sahticonnect.dashboard.entity.StatutAlerte;
import tn.sahticonnect.dashboard.repository.AlerteRepository;
import tn.sahticonnect.dashboard.repository.PatientSuiviRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Recalcul périodique avec une horloge fixe que le test fait avancer : sans nouvelle alerte, le niveau
 * baisse quand l'alerte dépasse 24 h, puis 7 jours.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecalculPeriodiqueTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

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
            return TUNIS;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Mock
    private PatientSuiviRepository patientRepository;
    @Mock
    private AlerteRepository alerteRepository;

    private final HorlogeReglable horloge = new HorlogeReglable();
    private PatientService service;
    private final PatientSuivi hedi = PatientSuivi.builder().id("PAT-0005").niveauRisque(NiveauRisque.FAIBLE).build();

    @BeforeEach
    void preparer() {
        service = new PatientService(patientRepository, alerteRepository, horloge);
        Alerte chute = new Alerte();
        chute.setPatientId("PAT-0005");
        chute.setNiveau(NiveauAlerte.CRITIQUE);
        chute.setStatut(StatutAlerte.NOUVELLE);
        chute.setHorodatage(OffsetDateTime.now(horloge));
        when(patientRepository.findAll()).thenReturn(List.of(hedi));
        when(patientRepository.findById("PAT-0005")).thenReturn(Optional.of(hedi));
        // la base ne renvoie que les alertes postérieures à la date demandée
        when(alerteRepository.findByPatientIdAndHorodatageAfter(eq("PAT-0005"), any())).thenAnswer(appel -> {
            OffsetDateTime depuis = appel.getArgument(1);
            return chute.getHorodatage().isAfter(depuis) ? List.of(chute) : List.of();
        });
    }

    @Test
    void leNiveauBaisseAvecLeTempsSansNouvelleAlerte() {
        assertThat(service.recalculerTous()).isEqualTo(1);
        assertThat(hedi.getNiveauRisque()).isEqualTo(NiveauRisque.CRITIQUE);

        horloge.avancer(Duration.ofHours(23));
        assertThat(service.recalculerTous()).isZero();
        assertThat(hedi.getNiveauRisque()).isEqualTo(NiveauRisque.CRITIQUE);

        horloge.avancer(Duration.ofHours(2)); // 25 h : l'alerte ne compte plus pour CRITIQUE
        assertThat(service.recalculerTous()).isEqualTo(1);
        assertThat(hedi.getNiveauRisque()).isEqualTo(NiveauRisque.MODERE);

        horloge.avancer(Duration.ofDays(7)); // plus aucune alerte dans les 7 jours
        assertThat(service.recalculerTous()).isEqualTo(1);
        assertThat(hedi.getNiveauRisque()).isEqualTo(NiveauRisque.FAIBLE);
    }
}
