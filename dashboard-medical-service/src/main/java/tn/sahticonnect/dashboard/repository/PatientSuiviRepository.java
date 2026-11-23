package tn.sahticonnect.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.sahticonnect.dashboard.entity.PatientSuivi;

public interface PatientSuiviRepository extends JpaRepository<PatientSuivi, String>,
        JpaSpecificationExecutor<PatientSuivi> {
}
