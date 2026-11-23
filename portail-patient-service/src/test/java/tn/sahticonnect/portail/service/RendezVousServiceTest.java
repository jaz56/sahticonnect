package tn.sahticonnect.portail.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.entity.RendezVous;
import tn.sahticonnect.portail.entity.StatutRendezVous;
import tn.sahticonnect.portail.entity.TypeConsultation;
import tn.sahticonnect.portail.exception.ConflitException;
import tn.sahticonnect.portail.exception.PortailException;
import tn.sahticonnect.portail.exception.RequeteInvalideException;
import tn.sahticonnect.portail.repository.CreneauRepository;
import tn.sahticonnect.portail.repository.MedecinRepository;
import tn.sahticonnect.portail.repository.PatientRepository;
import tn.sahticonnect.portail.repository.RendezVousRepository;
import tn.sahticonnect.portail.service.model.DemandeRendezVous;
import tn.sahticonnect.portail.service.model.ModificationRendezVous;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires de RendezVousService : dépôts simulés (Mockito) et horloge fixe. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RendezVousServiceTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    /** « Maintenant » pour tous les tests : mardi 24/11/2026 à 10:00, heure de Tunis. */
    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Mock
    private RendezVousRepository rendezVousRepository;
    @Mock
    private CreneauRepository creneauRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private MedecinRepository medecinRepository;
    @Mock
    private GenerateurIdentifiants identifiants;

    private RendezVousService service;

    private final Medecin chaabane = Medecin.builder().id("MED-001").nom("Chaabane").prenom("Mohamed").build();
    private final Medecin haddad = Medecin.builder().id("MED-002").nom("Haddad").prenom("Ines").build();
    private final Patient leila = Patient.builder().id("PAT-0002").nom("Trabelsi").prenom("Leila")
            .medecinTraitant(haddad).build();

    @BeforeEach
    void preparer() {
        Clock horloge = Clock.fixed(MAINTENANT.atZone(TUNIS).toInstant(), TUNIS);
        service = new RendezVousService(rendezVousRepository, creneauRepository, patientRepository,
                medecinRepository, identifiants, horloge);
        when(patientRepository.findById("PAT-0002")).thenReturn(Optional.of(leila));
        when(medecinRepository.findById("MED-001")).thenReturn(Optional.of(chaabane));
        when(medecinRepository.findById("MED-002")).thenReturn(Optional.of(haddad));
        when(identifiants.rendezVous()).thenReturn("RDV-2026-000100");
        when(rendezVousRepository.save(any(RendezVous.class))).thenAnswer(appel -> appel.getArgument(0));
    }

    private static Creneau creneau(String id, Medecin medecin, LocalDateTime debut, TypeConsultation type) {
        return new Creneau(id, medecin, debut, debut.plusMinutes(30), type);
    }

    private static String code(Throwable erreur) {
        return ((PortailException) erreur).getCode();
    }

    @Nested
    class Urgence {

        @Test
        void premierCreneauLibreDansLes2h_chezLeMedecinTraitantParDefaut() {
            Creneau garde = creneau("CRN-20261124-002-1015", haddad, MAINTENANT.plusMinutes(15), TypeConsultation.URGENCE);
            when(creneauRepository.findFirstByMedecinIdAndTypeAndDisponibleTrueAndDebutBetweenOrderByDebutAsc(
                    eq("MED-002"), eq(TypeConsultation.URGENCE), eq(MAINTENANT), eq(MAINTENANT.plusHours(2))))
                    .thenReturn(Optional.of(garde));

            RendezVous rdv = service.creer(new DemandeRendezVous("PAT-0002", null, null, TypeConsultation.URGENCE,
                    "Alerte ÉLEVÉE : tension systolique 172 mmHg", "ALR-2026-000317"));

            assertThat(rdv.getId()).isEqualTo("RDV-2026-000100");
            assertThat(rdv.getMedecin()).isSameAs(haddad);
            assertThat(rdv.getCreneau()).isSameAs(garde);
            assertThat(rdv.getDebut()).isEqualTo(LocalDateTime.of(2026, 11, 24, 10, 15));
            assertThat(rdv.getType()).isEqualTo(TypeConsultation.URGENCE);
            assertThat(rdv.getStatut()).isEqualTo(StatutRendezVous.CONFIRME);
            assertThat(rdv.getReferenceAlerte()).isEqualTo("ALR-2026-000317");
            assertThat(garde.isDisponible()).isFalse();
            verify(creneauRepository).saveAndFlush(garde);
        }

        @Test
        void medecinExplicite_prioritaireSurLeMedecinTraitant() {
            Creneau garde = creneau("CRN-20261124-001-1045", chaabane, MAINTENANT.plusMinutes(45), TypeConsultation.URGENCE);
            when(creneauRepository.findFirstByMedecinIdAndTypeAndDisponibleTrueAndDebutBetweenOrderByDebutAsc(
                    eq("MED-001"), eq(TypeConsultation.URGENCE), any(), any())).thenReturn(Optional.of(garde));

            RendezVous rdv = service.creer(new DemandeRendezVous("PAT-0002", "MED-001", null,
                    TypeConsultation.URGENCE, null, null));

            assertThat(rdv.getMedecin()).isSameAs(chaabane);
        }

        @Test
        void aucunCreneauLibreDansLes2h_409() {
            when(creneauRepository.findFirstByMedecinIdAndTypeAndDisponibleTrueAndDebutBetweenOrderByDebutAsc(
                    any(), any(), any(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(new DemandeRendezVous("PAT-0002", null, null,
                    TypeConsultation.URGENCE, null, null)))
                    .isInstanceOf(ConflitException.class)
                    .satisfies(e -> assertThat(code(e)).isEqualTo("AUCUN_CRENEAU_URGENCE"))
                    .hasMessage("Aucun créneau libre dans les 2 h pour MED-002");
        }
    }

    @Nested
    class Standard {

        private final Creneau creneau = creneau("CRN-20261125-001-0900", null, LocalDateTime.of(2026, 11, 25, 9, 0),
                TypeConsultation.PRESENTIEL);

        @BeforeEach
        void preparer() {
            creneau.setMedecin(chaabane);
            when(creneauRepository.findById(creneau.getId())).thenReturn(Optional.of(creneau));
        }

        private DemandeRendezVous demande(TypeConsultation type) {
            return new DemandeRendezVous("PAT-0002", "MED-001", creneau.getId(), type, "Contrôle", null);
        }

        @Test
        void creneauLibre_reserve() {
            RendezVous rdv = service.creer(demande(TypeConsultation.PRESENTIEL));

            assertThat(rdv.getCreneau()).isSameAs(creneau);
            assertThat(creneau.isDisponible()).isFalse();
        }

        @Test
        void creneauDejaReserve_409() {
            creneau.setDisponible(false);

            assertThatThrownBy(() -> service.creer(demande(TypeConsultation.PRESENTIEL)))
                    .satisfies(e -> assertThat(code(e)).isEqualTo("CRENEAU_DEJA_RESERVE"));
        }

        @Test
        void conflitDeVersionAuFlush_409() {
            // une autre transaction a réservé le même créneau entre la lecture et l'écriture
            when(creneauRepository.saveAndFlush(creneau))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Creneau.class, creneau.getId()));

            assertThatThrownBy(() -> service.creer(demande(TypeConsultation.PRESENTIEL)))
                    .isInstanceOf(ConflitException.class)
                    .satisfies(e -> assertThat(code(e)).isEqualTo("CRENEAU_DEJA_RESERVE"));
        }

        @Test
        void typeIncompatible_400() {
            assertThatThrownBy(() -> service.creer(demande(TypeConsultation.TELECONSULTATION)))
                    .isInstanceOf(RequeteInvalideException.class)
                    .satisfies(e -> assertThat(code(e)).isEqualTo("TYPE_CRENEAU_INCOMPATIBLE"));
        }

        @Test
        void sansCreneauId_400() {
            assertThatThrownBy(() -> service.creer(new DemandeRendezVous("PAT-0002", "MED-001", null,
                    TypeConsultation.PRESENTIEL, null, null)))
                    .isInstanceOf(RequeteInvalideException.class);
        }
    }

    @Nested
    class ModificationEtAnnulation {

        private final Creneau ancien = creneau("CRN-20261125-001-0900", null, LocalDateTime.of(2026, 11, 25, 9, 0),
                TypeConsultation.PRESENTIEL);
        private final Creneau nouveau = creneau("CRN-20261126-001-1000", null, LocalDateTime.of(2026, 11, 26, 10, 0),
                TypeConsultation.PRESENTIEL);
        private final RendezVous rdv = new RendezVous();

        @BeforeEach
        void preparer() {
            ancien.setMedecin(chaabane);
            nouveau.setMedecin(chaabane);
            ancien.setDisponible(false);
            rdv.setId("RDV-2026-000042");
            rdv.setPatient(leila);
            rdv.setMedecin(chaabane);
            rdv.setCreneau(ancien);
            rdv.setDebut(ancien.getDebut());
            rdv.setFin(ancien.getFin());
            rdv.setType(TypeConsultation.PRESENTIEL);
            rdv.setStatut(StatutRendezVous.CONFIRME);
            when(rendezVousRepository.findById(rdv.getId())).thenReturn(Optional.of(rdv));
            when(creneauRepository.findById(nouveau.getId())).thenReturn(Optional.of(nouveau));
        }

        @Test
        void changerDeCreneau_libereLAncien() {
            service.modifier(rdv.getId(), new ModificationRendezVous(nouveau.getId(), null, null, null));

            assertThat(rdv.getCreneau()).isSameAs(nouveau);
            assertThat(rdv.getDebut()).isEqualTo(nouveau.getDebut());
            assertThat(nouveau.isDisponible()).isFalse();
            assertThat(ancien.isDisponible()).isTrue();
        }

        @Test
        void annulation_statutAnnuleEtCreneauLibere() {
            service.annuler(rdv.getId());

            assertThat(rdv.getStatut()).isEqualTo(StatutRendezVous.ANNULE);
            assertThat(ancien.isDisponible()).isTrue();
        }

        @Test
        void annulationDUnRdvTermine_409() {
            rdv.setStatut(StatutRendezVous.TERMINE);

            assertThatThrownBy(() -> service.annuler(rdv.getId()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo("RDV_NON_ANNULABLE"))
                    .hasMessage("Le rendez-vous RDV-2026-000042 est déjà au statut TERMINE");
            assertThat(ancien.isDisponible()).isFalse();
        }

        @Test
        void annulationDUnRdvDejaAnnule_409() {
            rdv.setStatut(StatutRendezVous.ANNULE);

            assertThatThrownBy(() -> service.annuler(rdv.getId()))
                    .satisfies(e -> assertThat(code(e)).isEqualTo("RDV_NON_ANNULABLE"));
        }
    }
}
