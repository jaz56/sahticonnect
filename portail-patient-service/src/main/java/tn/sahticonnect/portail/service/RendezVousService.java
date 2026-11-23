package tn.sahticonnect.portail.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.entity.RendezVous;
import tn.sahticonnect.portail.entity.StatutRendezVous;
import tn.sahticonnect.portail.entity.TypeConsultation;
import tn.sahticonnect.portail.exception.ConflitException;
import tn.sahticonnect.portail.exception.RequeteInvalideException;
import tn.sahticonnect.portail.exception.RessourceIntrouvableException;
import tn.sahticonnect.portail.repository.CreneauRepository;
import tn.sahticonnect.portail.repository.MedecinRepository;
import tn.sahticonnect.portail.repository.PatientRepository;
import tn.sahticonnect.portail.repository.RendezVousRepository;
import tn.sahticonnect.portail.service.model.DemandeRendezVous;
import tn.sahticonnect.portail.service.model.FiltreRendezVous;
import tn.sahticonnect.portail.service.model.ModificationRendezVous;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Prise, modification et annulation de rendez-vous. */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RendezVousService {

    /** Un RDV d'urgence doit commencer dans les 2 h. */
    public static final Duration DELAI_URGENCE = Duration.ofHours(2);

    private final RendezVousRepository rendezVousRepository;
    private final CreneauRepository creneauRepository;
    private final PatientRepository patientRepository;
    private final MedecinRepository medecinRepository;
    private final GenerateurIdentifiants identifiants;
    private final Clock horloge;

    // ===================== Lecture =====================

    @Transactional(readOnly = true)
    public List<RendezVous> lister(FiltreRendezVous filtre) {
        Specification<RendezVous> spec = (racine, requete, cb) -> cb.conjunction();
        if (filtre.patientId() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("patient").get("id"), filtre.patientId()));
        }
        if (filtre.medecinId() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("medecin").get("id"), filtre.medecinId()));
        }
        if (filtre.date() != null) {
            spec = spec.and((r, q, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(r.get("debut"), filtre.date().atStartOfDay()),
                    cb.lessThan(r.get("debut"), filtre.date().plusDays(1).atStartOfDay())));
        }
        if (filtre.statut() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("statut"), filtre.statut()));
        }
        return rendezVousRepository.findAll(spec, Sort.by("debut"));
    }

    @Transactional(readOnly = true)
    public RendezVous consulter(String id) {
        return rendezVousRepository.findById(id).orElseThrow(() -> RessourceIntrouvableException.rendezVous(id));
    }

    // ===================== Création =====================

    /**
     * Standard : medecinId et creneauId obligatoires ; le créneau doit exister, appartenir au médecin,
     * être libre et du type demandé.
     * Urgence (type URGENCE sans creneauId) : médecin traitant par défaut, premier créneau URGENCE libre
     * dans les 2 h, sinon 409 AUCUN_CRENEAU_URGENCE.
     */
    public RendezVous creer(DemandeRendezVous demande) {
        Patient patient = patientRepository.findById(demande.patientId())
                .orElseThrow(() -> RessourceIntrouvableException.patient(demande.patientId()));

        if (demande.type() == TypeConsultation.URGENCE && demande.creneauId() == null) {
            return creerUrgence(patient, demande);
        }
        if (demande.creneauId() == null || (demande.medecinId() == null && demande.type() != TypeConsultation.URGENCE)) {
            throw new RequeteInvalideException(
                    "medecinId et creneauId sont obligatoires, sauf pour un rendez-vous d'urgence");
        }
        Creneau creneau = chargerCreneau(demande.creneauId());
        if (demande.medecinId() != null) {
            Medecin medecin = chargerMedecin(demande.medecinId());
            if (!creneau.getMedecin().getId().equals(medecin.getId())) {
                throw new RequeteInvalideException("CRENEAU_INCOHERENT",
                        "Le créneau " + creneau.getId() + " n'appartient pas au médecin " + medecin.getId());
            }
        }
        verifierType(creneau, demande.type());
        return reserver(patient, creneau, demande.type(), demande.motif(), demande.referenceAlerte());
    }

    private RendezVous creerUrgence(Patient patient, DemandeRendezVous demande) {
        Medecin medecin = demande.medecinId() != null ? chargerMedecin(demande.medecinId()) : patient.getMedecinTraitant();
        if (medecin == null) {
            throw new RequeteInvalideException("Le patient " + patient.getId()
                    + " n'a pas de médecin traitant : medecinId est obligatoire");
        }
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        Creneau creneau = creneauRepository
                .findFirstByMedecinIdAndTypeAndDisponibleTrueAndDebutBetweenOrderByDebutAsc(
                        medecin.getId(), TypeConsultation.URGENCE, maintenant, maintenant.plus(DELAI_URGENCE))
                .orElseThrow(() -> new ConflitException("AUCUN_CRENEAU_URGENCE",
                        "Aucun créneau libre dans les 2 h pour " + medecin.getId()));
        return reserver(patient, creneau, TypeConsultation.URGENCE, demande.motif(), demande.referenceAlerte());
    }

    private RendezVous reserver(Patient patient, Creneau creneau, TypeConsultation type,
                                String motif, String referenceAlerte) {
        occuper(creneau);
        RendezVous rdv = new RendezVous();
        rdv.setId(identifiants.rendezVous());
        rdv.setPatient(patient);
        rdv.setMedecin(creneau.getMedecin());
        rdv.setCreneau(creneau);
        rdv.setDebut(creneau.getDebut());
        rdv.setFin(creneau.getFin());
        rdv.setType(type);
        rdv.setStatut(StatutRendezVous.CONFIRME);
        rdv.setMotif(motif);
        rdv.setReferenceAlerte(referenceAlerte);
        rdv.setDateCreation(LocalDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS));
        return rendezVousRepository.save(rdv);
    }

    // ===================== Modification / annulation =====================

    /**
     * Changer de créneau réserve le nouveau puis libère l'ancien. Le nouveau créneau doit appartenir au
     * même médecin (400 CRENEAU_INCOHERENT) : changer de médecin = annuler puis recréer le rendez-vous.
     */
    public RendezVous modifier(String id, ModificationRendezVous modification) {
        RendezVous rdv = consulter(id);
        if (modification.estVide()) {
            throw new RequeteInvalideException("Au moins un champ doit être modifié");
        }
        if (rdv.getStatut() != StatutRendezVous.CONFIRME) {
            throw new ConflitException("RDV_NON_MODIFIABLE",
                    "Le rendez-vous " + id + " est au statut " + rdv.getStatut());
        }
        if (modification.statut() != null
                && modification.statut() != StatutRendezVous.TERMINE && modification.statut() != StatutRendezVous.ABSENT) {
            throw new RequeteInvalideException("Statut " + modification.statut()
                    + " interdit : l'annulation passe par DELETE");
        }
        TypeConsultation type = modification.type() != null ? modification.type() : rdv.getType();

        boolean changementCreneau = modification.creneauId() != null
                && (rdv.getCreneau() == null || !modification.creneauId().equals(rdv.getCreneau().getId()));
        if (changementCreneau) {
            Creneau nouveau = chargerCreneau(modification.creneauId());
            if (!nouveau.getMedecin().getId().equals(rdv.getMedecin().getId())) {
                throw new RequeteInvalideException("CRENEAU_INCOHERENT", "Le créneau " + nouveau.getId()
                        + " n'appartient pas au médecin " + rdv.getMedecin().getId()
                        + " du rendez-vous : pour changer de médecin, annuler puis recréer le rendez-vous");
            }
            verifierType(nouveau, type);
            occuper(nouveau);
            liberer(rdv.getCreneau());
            rdv.setCreneau(nouveau);
            rdv.setMedecin(nouveau.getMedecin());
            rdv.setDebut(nouveau.getDebut());
            rdv.setFin(nouveau.getFin());
        } else if (rdv.getCreneau() != null) {
            verifierType(rdv.getCreneau(), type);
        }
        rdv.setType(type);
        if (modification.motif() != null) {
            rdv.setMotif(modification.motif());
        }
        if (modification.statut() != null) {
            rdv.setStatut(modification.statut());
        }
        return rdv;
    }

    /** Annulation logique : statut ANNULE et créneau libéré. */
    public RendezVous annuler(String id) {
        RendezVous rdv = consulter(id);
        if (rdv.getStatut() == StatutRendezVous.TERMINE || rdv.getStatut() == StatutRendezVous.ANNULE) {
            throw new ConflitException("RDV_NON_ANNULABLE",
                    "Le rendez-vous " + id + " est déjà au statut " + rdv.getStatut());
        }
        rdv.setStatut(StatutRendezVous.ANNULE);
        liberer(rdv.getCreneau());
        return rdv;
    }

    // ===================== Créneaux =====================

    /**
     * Marque le créneau occupé et écrit immédiatement (flush) : si une autre transaction l'a réservé
     * entre-temps, la version ne correspond plus et l'UPDATE échoue → 409 CRENEAU_DEJA_RESERVE.
     */
    private void occuper(Creneau creneau) {
        if (!creneau.isDisponible()) {
            throw ConflitException.creneauDejaReserve(creneau.getId());
        }
        creneau.setDisponible(false);
        try {
            creneauRepository.saveAndFlush(creneau);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Verrou optimiste : le créneau {} a été réservé par une autre requête", creneau.getId());
            throw ConflitException.creneauDejaReserve(creneau.getId());
        }
    }

    private void liberer(Creneau creneau) {
        if (creneau != null) {
            creneau.setDisponible(true);
        }
    }

    private void verifierType(Creneau creneau, TypeConsultation type) {
        if (creneau.getType() != type) {
            throw new RequeteInvalideException("TYPE_CRENEAU_INCOMPATIBLE",
                    "Le créneau " + creneau.getId() + " est de type " + creneau.getType() + ", pas " + type);
        }
    }

    private Creneau chargerCreneau(String id) {
        return creneauRepository.findById(id).orElseThrow(() -> RessourceIntrouvableException.creneau(id));
    }

    private Medecin chargerMedecin(String id) {
        return medecinRepository.findById(id).orElseThrow(() -> RessourceIntrouvableException.medecin(id));
    }
}
