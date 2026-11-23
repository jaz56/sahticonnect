package tn.sahticonnect.portail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Rendez-vous (RDV-AAAA-NNNNNN). Début et fin sont recopiés du créneau réservé. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RendezVous extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 15)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medecin_id")
    private Medecin medecin;

    /** Créneau réservé (null pour un rendez-vous ancien saisi sans créneau). */
    @ManyToOne
    @JoinColumn(name = "creneau_id")
    private Creneau creneau;

    @Column(nullable = false)
    private LocalDateTime debut;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TypeConsultation type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private StatutRendezVous statut;

    @Column(length = 500)
    private String motif;

    @Column(name = "reference_alerte")
    private String referenceAlerte;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;
}
