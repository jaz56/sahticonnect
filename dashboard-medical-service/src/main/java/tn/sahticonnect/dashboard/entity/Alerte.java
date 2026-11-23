package tn.sahticonnect.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Alerte enregistrée par l'orchestrateur ; l'identifiant est celui de l'alerte gRPC (ALR-…). */
@Entity
@Table(indexes = @Index(name = "idx_alerte_patient", columnList = "patient_id, horodatage"))
@Getter
@Setter
@NoArgsConstructor
public class Alerte extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 15)
    private String id;

    @Column(name = "patient_id", nullable = false, length = 8)
    private String patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private NiveauAlerte niveau;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatutAlerte statut;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_constante", nullable = false, length = 20)
    private TypeConstante typeConstante;

    @Column(nullable = false)
    private double valeur;

    @Column(name = "seuil_franchi", nullable = false)
    private String seuilFranchi;

    @Column(length = 500)
    private String message;

    @Column(nullable = false)
    private OffsetDateTime horodatage;

    /** Renseignés par resoudreAlerte (non exposés dans le schéma GraphQL actuel). */
    @Column(name = "commentaire_resolution", length = 500)
    private String commentaireResolution;

    @Column(name = "date_resolution")
    private OffsetDateTime dateResolution;
}
