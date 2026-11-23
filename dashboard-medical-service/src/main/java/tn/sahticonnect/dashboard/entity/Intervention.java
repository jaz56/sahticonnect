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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Intervention (INT-AAAA-NNNNNN), créée puis mise à jour par l'orchestrateur au fil du workflow. */
@Entity
@Table(indexes = {
        @Index(name = "idx_intervention_patient", columnList = "patient_id"),
        @Index(name = "idx_intervention_alerte", columnList = "alerte_id")})
@Getter
@Setter
@NoArgsConstructor
public class Intervention extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 15)
    private String id;

    @Column(name = "patient_id", nullable = false, length = 8)
    private String patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TypeIntervention type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 23)
    private EtatWorkflow etat;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_facturation", nullable = false, length = 13)
    private StatutFacturation statutFacturation;

    /** Alerte à l'origine de l'intervention (null si créée manuellement). */
    @Column(name = "alerte_id", length = 15)
    private String alerteId;

    @Column(length = 500)
    private String description;

    @Column(name = "reference_externe", length = 20)
    private String referenceExterne;

    @Column(name = "numero_feuille_soins", length = 14)
    private String numeroFeuilleSoins;

    @Column(name = "montant_total", precision = 12, scale = 3)
    private BigDecimal montantTotal;

    @Column(name = "part_cnam", precision = 12, scale = 3)
    private BigDecimal partCNAM;

    @Column(name = "part_patient", precision = 12, scale = 3)
    private BigDecimal partPatient;

    @Column(name = "date_creation", nullable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_mise_a_jour")
    private OffsetDateTime dateMiseAJour;
}
