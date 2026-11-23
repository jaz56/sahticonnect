package tn.sahticonnect.portail.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.portail.entity.Ordonnance;

import java.time.LocalDate;
import java.util.List;

public interface OrdonnanceRepository extends JpaRepository<Ordonnance, String> {

    @EntityGraph(attributePaths = "lignes")
    List<Ordonnance> findByPatientIdOrderByDateEmissionDesc(String patientId);

    /** Ordonnances encore valides à la date donnée. */
    @EntityGraph(attributePaths = "lignes")
    List<Ordonnance> findByPatientIdAndDateFinValiditeGreaterThanEqualOrderByDateEmissionDesc(
            String patientId, LocalDate date);
}
