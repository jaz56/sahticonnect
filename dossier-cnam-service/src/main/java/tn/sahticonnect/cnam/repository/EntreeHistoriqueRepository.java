package tn.sahticonnect.cnam.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.cnam.entity.EntreeHistorique;

import java.time.LocalDate;
import java.util.List;

public interface EntreeHistoriqueRepository extends JpaRepository<EntreeHistorique, Long> {

    /** Entrées d'un patient entre deux dates (incluses), de la plus récente à la plus ancienne. */
    @EntityGraph(attributePaths = "actes")
    List<EntreeHistorique> findByAssureIdentifiantPatientAndDateBetweenOrderByDateDescIdDesc(
            String identifiantPatient, LocalDate debut, LocalDate fin);
}
