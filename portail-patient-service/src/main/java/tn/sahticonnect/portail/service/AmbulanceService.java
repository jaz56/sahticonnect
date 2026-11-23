package tn.sahticonnect.portail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.entity.MissionAmbulance;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.entity.StatutMission;
import tn.sahticonnect.portail.exception.ConflitException;
import tn.sahticonnect.portail.exception.RessourceIntrouvableException;
import tn.sahticonnect.portail.repository.MissionAmbulanceRepository;
import tn.sahticonnect.portail.repository.PatientRepository;
import tn.sahticonnect.portail.service.model.DemandeAmbulance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Demandes d'ambulance. Appelé par l'orchestrateur sur alerte CRITIQUE : l'ambulance est envoyée
 * même si le patient n'est pas couvert par la CNAM.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AmbulanceService {

    /** Flotte simulée. */
    public static final List<String> VEHICULES =
            List.of("SAMU-TN-01", "SAMU-TN-02", "SAMU-TN-03", "SAMU-TN-04", "SAMU-TN-05");
    public static final int DELAI_MIN_MINUTES = 8;
    public static final int DELAI_MAX_MINUTES = 20;
    static final String ETABLISSEMENT_PAR_DEFAUT = "Hôpital Charles Nicolle";

    private final MissionAmbulanceRepository missionRepository;
    private final PatientRepository patientRepository;
    private final GenerateurIdentifiants identifiants;
    private final Clock horloge;
    private final Random aleatoire;

    public MissionAmbulance demander(DemandeAmbulance demande) {
        Patient patient = patientRepository.findById(demande.patientId())
                .orElseThrow(() -> RessourceIntrouvableException.patient(demande.patientId()));
        missionRepository.findFirstByPatientIdAndStatutIn(patient.getId(), StatutMission.EN_COURS)
                .ifPresent(enCours -> {
                    throw new ConflitException("MISSION_DEJA_EN_COURS",
                            "La mission " + enCours.getId() + " est déjà en cours pour " + patient.getId());
                });

        LocalDateTime maintenant = LocalDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
        MissionAmbulance mission = new MissionAmbulance();
        mission.setId(identifiants.mission());
        mission.setPatient(patient);
        mission.setReferenceAlerte(demande.referenceAlerte());
        mission.setMotif(demande.motif());
        mission.setAdresse(demande.adresse() != null ? demande.adresse() : patient.getAdresse());
        mission.setLatitude(demande.latitude());
        mission.setLongitude(demande.longitude());
        mission.setVehicule(vehiculeLibre());
        mission.setEtablissementDestination(destination(patient));
        mission.setDelaiEstimeMinutes(DELAI_MIN_MINUTES + aleatoire.nextInt(DELAI_MAX_MINUTES - DELAI_MIN_MINUTES + 1));
        mission.setStatut(StatutMission.DEPECHEE);
        mission.setDateDemande(maintenant);
        mission.setDateMiseAJour(maintenant);
        return missionRepository.save(mission);
    }

    @Transactional(readOnly = true)
    public MissionAmbulance consulter(String id) {
        return missionRepository.findById(id).orElseThrow(() -> RessourceIntrouvableException.mission(id));
    }

    /** Simulation : chaque mission en cours passe à l'étape suivante. Renvoie le nombre de missions modifiées. */
    public int faireProgresser() {
        List<MissionAmbulance> missions = missionRepository.findByStatutIn(
                Set.of(StatutMission.DEPECHEE, StatutMission.SUR_PLACE, StatutMission.TRANSPORT));
        LocalDateTime maintenant = LocalDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
        for (MissionAmbulance mission : missions) {
            mission.setStatut(mission.getStatut().suivant());
            mission.setDelaiEstimeMinutes(0); // à partir de SUR_PLACE, l'ambulance est arrivée
            mission.setDateMiseAJour(maintenant);
        }
        return missions.size();
    }

    /** Premier véhicule qui n'est affecté à aucune mission en cours. */
    private String vehiculeLibre() {
        Set<String> occupes = missionRepository.findByStatutIn(StatutMission.EN_COURS).stream()
                .map(MissionAmbulance::getVehicule)
                .collect(Collectors.toSet());
        return VEHICULES.stream()
                .filter(v -> !occupes.contains(v))
                .findFirst()
                .orElseThrow(() -> new ConflitException("AUCUN_VEHICULE_DISPONIBLE",
                        "Les " + VEHICULES.size() + " ambulances sont déjà en mission"));
    }

    /** Hôpital du médecin traitant s'il exerce à l'hôpital, sinon l'hôpital de référence. */
    private static String destination(Patient patient) {
        Medecin traitant = patient.getMedecinTraitant();
        if (traitant != null && traitant.getEtablissement() != null && traitant.getEtablissement().startsWith("Hôpital")) {
            return traitant.getEtablissement();
        }
        return ETABLISSEMENT_PAR_DEFAUT;
    }
}
