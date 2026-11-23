package tn.sahticonnect.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.dashboard.entity.Intervention;

import java.util.Collection;
import java.util.List;

public interface InterventionRepository extends JpaRepository<Intervention, String> {

    List<Intervention> findByPatientIdInOrderByDateCreationDesc(Collection<String> patientIds);

    List<Intervention> findByAlerteIdInOrderByDateCreationDesc(Collection<String> alerteIds);
}
