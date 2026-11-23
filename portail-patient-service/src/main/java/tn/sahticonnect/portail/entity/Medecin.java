package tn.sahticonnect.portail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Médecin de l'annuaire (MED-001…). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medecin extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 7)
    private String id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private String specialite;

    private String etablissement;

    private String ville;

    @Enumerated(EnumType.STRING)
    @Column(length = 13)
    private Filiere filiere;

    private boolean teleconsultation;

    @Column(name = "tarif_consultation", precision = 12, scale = 3)
    private BigDecimal tarifConsultation;

    /** Numéro à 3 chiffres utilisé dans l'identifiant des créneaux (MED-001 → 001). */
    public String numero() {
        return id.substring("MED-".length());
    }

    /** « Dr Mohamed Chaabane ». */
    public String nomComplet() {
        return "Dr " + prenom + " " + nom;
    }
}
