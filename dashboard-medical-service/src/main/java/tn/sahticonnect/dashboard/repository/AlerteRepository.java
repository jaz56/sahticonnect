package tn.sahticonnect.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.dashboard.entity.Alerte;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface AlerteRepository extends JpaRepository<Alerte, String> {

    /** Une seule requête pour tous les patients d'une réponse (chargement groupé). */
    List<Alerte> findByPatientIdInOrderByHorodatageDesc(Collection<String> patientIds);

    List<Alerte> findByIdIn(Collection<String> ids);

    List<Alerte> findByPatientIdAndHorodatageAfter(String patientId, OffsetDateTime depuis);
}
