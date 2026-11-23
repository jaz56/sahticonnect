package tn.sahticonnect.cnam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.cnam.entity.FeuilleDeSoins;

import java.util.Optional;

public interface FeuilleDeSoinsRepository extends JpaRepository<FeuilleDeSoins, Long> {

    Optional<FeuilleDeSoins> findByNumero(String numero);
}
