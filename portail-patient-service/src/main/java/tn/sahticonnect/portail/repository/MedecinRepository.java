package tn.sahticonnect.portail.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.portail.entity.Medecin;

public interface MedecinRepository extends JpaRepository<Medecin, String> {
}
