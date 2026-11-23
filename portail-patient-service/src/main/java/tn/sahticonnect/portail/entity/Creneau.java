package tn.sahticonnect.portail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Créneau de 30 min d'un médecin (CRN-aaaaMMjj-NNN-HHmm).
 * <p>
 * Verrou optimiste : {@code @Version} ajoute « AND version = ? » à chaque UPDATE et incrémente la
 * version. Si deux transactions réservent le même créneau en même temps, la seconde met à jour
 * 0 ligne et Hibernate lève une exception de verrou optimiste → 409, une seule réservation réussit.
 */
@Entity
@Table(indexes = @Index(name = "idx_creneau_medecin_debut", columnList = "medecin_id, debut"))
@Getter
@Setter
@NoArgsConstructor
public class Creneau {

    @Id
    @Column(length = 25)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medecin_id")
    private Medecin medecin;

    @Column(nullable = false)
    private LocalDateTime debut;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TypeConsultation type;

    private boolean disponible = true;

    @Version
    private Long version;

    public Creneau(String id, Medecin medecin, LocalDateTime debut, LocalDateTime fin, TypeConsultation type) {
        this.id = id;
        this.medecin = medecin;
        this.debut = debut;
        this.fin = fin;
        this.type = type;
    }
}
