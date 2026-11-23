package tn.sahticonnect.portail.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.TypeConsultation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CreneauRepository extends JpaRepository<Creneau, String> {

    /** Créneaux d'un médecin dont le début est dans [debut, fin[, triés par heure. */
    List<Creneau> findByMedecinIdAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut(
            String medecinId, LocalDateTime debut, LocalDateTime fin);

    /** Premier créneau libre d'un type donné dont le début est dans [debut, fin]. */
    Optional<Creneau> findFirstByMedecinIdAndTypeAndDisponibleTrueAndDebutBetweenOrderByDebutAsc(
            String medecinId, TypeConsultation type, LocalDateTime debut, LocalDateTime fin);
}
