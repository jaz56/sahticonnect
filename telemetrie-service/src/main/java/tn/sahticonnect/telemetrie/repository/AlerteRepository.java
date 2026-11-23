package tn.sahticonnect.telemetrie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.telemetrie.entity.Alerte;

import java.util.List;

public interface AlerteRepository extends JpaRepository<Alerte, String> {

    List<Alerte> findByIdentifiantPatientOrderByHorodatageDesc(String identifiantPatient);
}
