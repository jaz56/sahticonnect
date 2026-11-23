package tn.sahticonnect.portail.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.exception.ConflitException;
import tn.sahticonnect.portail.exception.RequeteInvalideException;
import tn.sahticonnect.portail.repository.OrdonnanceRepository;
import tn.sahticonnect.portail.repository.PatientRepository;
import tn.sahticonnect.portail.service.model.CoordonneesPatient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires de PatientService. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientServiceTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private OrdonnanceRepository ordonnanceRepository;

    private PatientService service;
    private final Patient ahmed = Patient.builder().id("PAT-0001").nom("Ben Salah").prenom("Ahmed")
            .telephone("+216 98 123 456").email("ahmed.bensalah@exemple.tn").build();

    @BeforeEach
    void preparer() {
        Clock horloge = Clock.fixed(LocalDate.of(2026, 11, 24).atStartOfDay(TUNIS).toInstant(), TUNIS);
        service = new PatientService(patientRepository, ordonnanceRepository, horloge);
        when(patientRepository.findById("PAT-0001")).thenReturn(Optional.of(ahmed));
    }

    @Test
    void seulesLesCoordonneesFourniesChangent() {
        service.modifierCoordonnees("PAT-0001", new CoordonneesPatient("+216 20 000 000", null, "Nouvelle adresse", null));

        assertThat(ahmed.getTelephone()).isEqualTo("+216 20 000 000");
        assertThat(ahmed.getAdresse()).isEqualTo("Nouvelle adresse");
        assertThat(ahmed.getEmail()).isEqualTo("ahmed.bensalah@exemple.tn");
        assertThat(ahmed.getNom()).isEqualTo("Ben Salah");
    }

    @Test
    void emailDejaUtilise_409() {
        when(patientRepository.existsByEmailIgnoreCaseAndIdNot("leila.trabelsi@exemple.tn", "PAT-0001")).thenReturn(true);

        assertThatThrownBy(() -> service.modifierCoordonnees("PAT-0001",
                new CoordonneesPatient(null, "leila.trabelsi@exemple.tn", null, null)))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("déjà associé à un autre patient");
        assertThat(ahmed.getEmail()).isEqualTo("ahmed.bensalah@exemple.tn");
    }

    @Test
    void aucuneCoordonnee_400() {
        assertThatThrownBy(() -> service.modifierCoordonnees("PAT-0001", new CoordonneesPatient(null, null, null, null)))
                .isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void ordonnancesActives_filtreeSurLaDateDuJour() {
        service.ordonnances("PAT-0001", true);

        verify(ordonnanceRepository).findByPatientIdAndDateFinValiditeGreaterThanEqualOrderByDateEmissionDesc(
                "PAT-0001", LocalDate.of(2026, 11, 24));
    }
}
