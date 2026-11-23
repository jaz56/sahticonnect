package tn.sahticonnect.portail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.Ordonnance;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.exception.ConflitException;
import tn.sahticonnect.portail.exception.RequeteInvalideException;
import tn.sahticonnect.portail.exception.RessourceIntrouvableException;
import tn.sahticonnect.portail.repository.OrdonnanceRepository;
import tn.sahticonnect.portail.repository.PatientRepository;
import tn.sahticonnect.portail.service.model.CoordonneesPatient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Profil patient et ordonnances. */
@Service
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final OrdonnanceRepository ordonnanceRepository;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public Patient consulter(String id) {
        return patientRepository.findById(id).orElseThrow(() -> RessourceIntrouvableException.patient(id));
    }

    /** Seules les coordonnées changent ; l'identité et la couverture relèvent du service CNAM. */
    public Patient modifierCoordonnees(String id, CoordonneesPatient coordonnees) {
        Patient patient = consulter(id);
        if (coordonnees.estVide()) {
            throw new RequeteInvalideException("Au moins une coordonnée doit être fournie");
        }
        if (coordonnees.email() != null && patientRepository.existsByEmailIgnoreCaseAndIdNot(coordonnees.email(), id)) {
            throw new ConflitException("EMAIL_DEJA_UTILISE",
                    "L'email " + coordonnees.email() + " est déjà associé à un autre patient");
        }
        if (coordonnees.telephone() != null) {
            patient.setTelephone(coordonnees.telephone());
        }
        if (coordonnees.email() != null) {
            patient.setEmail(coordonnees.email());
        }
        if (coordonnees.adresse() != null) {
            patient.setAdresse(coordonnees.adresse());
        }
        if (coordonnees.contactUrgence() != null) {
            patient.setContactUrgence(coordonnees.contactUrgence());
        }
        return patient; // entité gérée : la mise à jour est écrite à la fin de la transaction
    }

    /** Ordonnances du patient, la plus récente en premier ; actives = dateFinValidite ≥ aujourd'hui. */
    @Transactional(readOnly = true)
    public List<Ordonnance> ordonnances(String patientId, boolean activesSeulement) {
        consulter(patientId);
        return activesSeulement
                ? ordonnanceRepository.findByPatientIdAndDateFinValiditeGreaterThanEqualOrderByDateEmissionDesc(
                        patientId, LocalDate.now(horloge))
                : ordonnanceRepository.findByPatientIdOrderByDateEmissionDesc(patientId);
    }
}
