package tn.sahticonnect.cnam.entity;

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

import java.time.LocalDate;

/** Assuré CNAM. L'identifiant est l'identifiant patient commun à tous les services. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assure {

    @Id
    @Column(name = "identifiant_patient", length = 8)
    private String identifiantPatient;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "matricule_cnam", nullable = false)
    private String matriculeCNAM;

    @Column(name = "code_vignette_cnam", nullable = false, unique = true, length = 14)
    private String codeVignetteCNAM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 13)
    private Filiere filiere;

    @Column(name = "date_fin_validite", nullable = false)
    private LocalDate dateFinValidite;

    @Column(nullable = false)
    private boolean apci;

    @Column(name = "code_apci")
    private String codeAPCI;
}
