package tn.sahticonnect.portail.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.entity.MissionAmbulance;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.entity.StatutMission;
import tn.sahticonnect.portail.exception.ConflitException;
import tn.sahticonnect.portail.exception.PortailException;
import tn.sahticonnect.portail.repository.MissionAmbulanceRepository;
import tn.sahticonnect.portail.repository.PatientRepository;
import tn.sahticonnect.portail.service.model.DemandeAmbulance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Tests unitaires d'AmbulanceService. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmbulanceServiceTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 11, 24, 9, 48);

    @Mock
    private MissionAmbulanceRepository missionRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private GenerateurIdentifiants identifiants;

    private AmbulanceService service;
    private final List<MissionAmbulance> missionsEnCours = new ArrayList<>();

    private final Patient ahmed = Patient.builder().id("PAT-0001").adresse("12 rue de Marseille, 1000 Tunis")
            .medecinTraitant(Medecin.builder().id("MED-001").etablissement("Hôpital Charles Nicolle").build())
            .build();

    @BeforeEach
    void preparer() {
        Clock horloge = Clock.fixed(MAINTENANT.atZone(TUNIS).toInstant(), TUNIS);
        service = new AmbulanceService(missionRepository, patientRepository, identifiants, horloge, new Random(42));
        when(patientRepository.findById("PAT-0001")).thenReturn(Optional.of(ahmed));
        when(identifiants.mission()).thenReturn("AMB-2026-000007");
        when(missionRepository.save(any(MissionAmbulance.class))).thenAnswer(appel -> appel.getArgument(0));
        when(missionRepository.findFirstByPatientIdAndStatutIn(eq("PAT-0001"), anyCollection()))
                .thenReturn(Optional.empty());
        when(missionRepository.findByStatutIn(anyCollection())).thenReturn(missionsEnCours);
    }

    private static MissionAmbulance mission(String id, String vehicule, StatutMission statut) {
        MissionAmbulance m = new MissionAmbulance();
        m.setId(id);
        m.setVehicule(vehicule);
        m.setStatut(statut);
        m.setDelaiEstimeMinutes(12);
        return m;
    }

    private DemandeAmbulance demande() {
        return new DemandeAmbulance("PAT-0001", "ALR-2026-000318", "Alerte CRITIQUE : fréquence cardiaque 142 bpm",
                null, 36.8008, 10.18);
    }

    @Test
    void missionDepecheeAvecAdresseDuProfilEtPremierVehiculeLibre() {
        missionsEnCours.add(mission("AMB-2026-000001", "SAMU-TN-01", StatutMission.SUR_PLACE));

        MissionAmbulance m = service.demander(demande());

        assertThat(m.getId()).isEqualTo("AMB-2026-000007");
        assertThat(m.getStatut()).isEqualTo(StatutMission.DEPECHEE);
        assertThat(m.getAdresse()).isEqualTo("12 rue de Marseille, 1000 Tunis");
        assertThat(m.getVehicule()).isEqualTo("SAMU-TN-02");
        assertThat(m.getDelaiEstimeMinutes()).isBetween(8, 20);
        assertThat(m.getEtablissementDestination()).isEqualTo("Hôpital Charles Nicolle");
        assertThat(m.getDateDemande()).isEqualTo(MAINTENANT);
        assertThat(m.getLatitude()).isEqualTo(36.8008);
    }

    @Test
    void adresseFournie_prioritaireSurLeProfil() {
        MissionAmbulance m = service.demander(new DemandeAmbulance("PAT-0001", null, "Chute", "Pharmacie centrale",
                null, null));

        assertThat(m.getAdresse()).isEqualTo("Pharmacie centrale");
    }

    @Test
    void missionDejaEnCours_409() {
        when(missionRepository.findFirstByPatientIdAndStatutIn(eq("PAT-0001"), anyCollection()))
                .thenReturn(Optional.of(mission("AMB-2026-000005", "SAMU-TN-03", StatutMission.TRANSPORT)));

        assertThatThrownBy(() -> service.demander(demande()))
                .isInstanceOf(ConflitException.class)
                .satisfies(e -> assertThat(((PortailException) e).getCode()).isEqualTo("MISSION_DEJA_EN_COURS"))
                .hasMessage("La mission AMB-2026-000005 est déjà en cours pour PAT-0001");
    }

    @Test
    void toutesLesAmbulancesOccupees_409() {
        for (int i = 1; i <= 5; i++) {
            missionsEnCours.add(mission("AMB-2026-00000" + i, "SAMU-TN-0" + i, StatutMission.DEPECHEE));
        }

        assertThatThrownBy(() -> service.demander(demande()))
                .satisfies(e -> assertThat(((PortailException) e).getCode()).isEqualTo("AUCUN_VEHICULE_DISPONIBLE"));
    }

    @Test
    void progression_chaqueMissionAvanceDUneEtape() {
        MissionAmbulance depechee = mission("AMB-2026-000001", "SAMU-TN-01", StatutMission.DEPECHEE);
        MissionAmbulance surPlace = mission("AMB-2026-000002", "SAMU-TN-02", StatutMission.SUR_PLACE);
        MissionAmbulance transport = mission("AMB-2026-000003", "SAMU-TN-03", StatutMission.TRANSPORT);
        missionsEnCours.addAll(List.of(depechee, surPlace, transport));

        int nombre = service.faireProgresser();

        assertThat(nombre).isEqualTo(3);
        assertThat(depechee.getStatut()).isEqualTo(StatutMission.SUR_PLACE);
        assertThat(depechee.getDelaiEstimeMinutes()).isZero();
        assertThat(surPlace.getStatut()).isEqualTo(StatutMission.TRANSPORT);
        assertThat(transport.getStatut()).isEqualTo(StatutMission.TERMINEE);
        assertThat(transport.getDateMiseAJour()).isEqualTo(MAINTENANT);
    }
}
