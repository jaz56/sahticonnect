package tn.sahticonnect.dashboard.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/** Entité à identifiant attribué par l'application (PAT-0001, ALR-…, INT-…) : persist direct, sans SELECT. */
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
