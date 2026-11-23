package tn.sahticonnect.telemetrie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;
import tn.sahticonnect.telemetrie.domaine.Constante;
import tn.sahticonnect.telemetrie.domaine.Niveau;

import java.time.Instant;

/**
 * Alerte émise (ALR-AAAA-NNNNNN), persistée pour l'historique.
 * Ne pas confondre avec tn.sahticonnect.telemetrie.Alerte, le message Protobuf diffusé aux abonnés.
 */
@Entity
@Table(indexes = @Index(name = "idx_alerte_patient", columnList = "identifiant_patient, horodatage"))
@Getter
@Setter
@NoArgsConstructor
public class Alerte implements Persistable<String> {

    @Id
    @Column(length = 15)
    private String id;

    @Column(name = "identifiant_patient", nullable = false, length = 8)
    private String identifiantPatient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Niveau niveau;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Constante constante;

    @Column(nullable = false)
    private double valeur;

    @Column(length = 10)
    private String unite;

    @Column(name = "seuil_franchi", nullable = false)
    private String seuilFranchi;

    @Column(nullable = false, length = 500)
    private String message;

    /** Horodatage de la mesure déclenchante. */
    @Column(nullable = false)
    private Instant horodatage;

    @Column(name = "capteur_id")
    private String capteurId;

    @Column(name = "sequence_mesure")
    private long sequenceMesure;

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
