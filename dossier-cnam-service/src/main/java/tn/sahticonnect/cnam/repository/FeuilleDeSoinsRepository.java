package tn.sahticonnect.cnam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.sahticonnect.cnam.entity.FeuilleDeSoins;

public interface FeuilleDeSoinsRepository extends JpaRepository<FeuilleDeSoins, Long> {

    /** Nombre de feuilles déjà émises avec ce préfixe (ex. « FS-2026- »), pour la numérotation. */
    long countByNumeroStartingWith(String prefixe);
}
