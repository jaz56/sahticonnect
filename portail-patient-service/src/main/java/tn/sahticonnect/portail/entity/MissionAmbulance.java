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

/** Mission d'ambulance (AMB-AAAA-NNNNNN). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MissionAmbulance extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 15)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "reference_alerte")
    private String referenceAlerte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private StatutMission statut;

    @Column(length = 500)
    private String motif;

    private String adresse;

    private Double latitude;

    private Double longitude;

    @Column(length = 12)
    private String vehicule;

    @Column(name = "etablissement_destination")
    private String etablissementDestination;

    @Column(name = "delai_estime_minutes")
    private Integer delaiEstimeMinutes;

    @Column(name = "date_demande", nullable = false)
    private LocalDateTime dateDemande;

    @Column(name = "date_mise_a_jour")
    private LocalDateTime dateMiseAJour;
}
