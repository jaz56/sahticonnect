package tn.sahticonnect.portail.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.portail.entity.Patient;

public interface PatientRepository extends JpaRepository<Patient, String> {

    /** Email déjà utilisé par un autre patient ? */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, String id);
}
