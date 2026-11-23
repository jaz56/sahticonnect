package tn.sahticonnect.portail.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.portail.entity.MissionAmbulance;
import tn.sahticonnect.portail.entity.StatutMission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionAmbulanceRepository extends JpaRepository<MissionAmbulance, String> {

    Optional<MissionAmbulance> findFirstByPatientIdAndStatutIn(String patientId, Collection<StatutMission> statuts);

    List<MissionAmbulance> findByStatutIn(Collection<StatutMission> statuts);
}
