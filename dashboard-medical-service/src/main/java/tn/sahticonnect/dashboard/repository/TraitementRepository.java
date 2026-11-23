package tn.sahticonnect.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.dashboard.entity.Traitement;

import java.util.Collection;
import java.util.List;

public interface TraitementRepository extends JpaRepository<Traitement, String> {

    List<Traitement> findByPatientIdInOrderByDateDebutDesc(Collection<String> patientIds);
}
