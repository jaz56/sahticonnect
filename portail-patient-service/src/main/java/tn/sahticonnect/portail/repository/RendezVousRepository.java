package tn.sahticonnect.portail.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.sahticonnect.portail.entity.RendezVous;

public interface RendezVousRepository extends JpaRepository<RendezVous, String>, JpaSpecificationExecutor<RendezVous> {
}
