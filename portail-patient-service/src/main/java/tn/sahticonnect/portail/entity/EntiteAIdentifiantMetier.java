package tn.sahticonnect.portail.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Entité dont l'identifiant est attribué par l'application (PAT-0001, RDV-2026-000042…).
 * Sans cela, Spring Data verrait un id non nul et ferait un merge (SELECT inutile) au lieu d'un persist.
 */
@MappedSuperclass
public abstract class EntiteAIdentifiantMetier implements Persistable<String> {

    @Transient
    private boolean nouvelle = true;

    @Override
    public boolean isNew() {
        return nouvelle;
    }

    @PostLoad
    @PostPersist
    void marquerExistante() {
        nouvelle = false;
    }
}
