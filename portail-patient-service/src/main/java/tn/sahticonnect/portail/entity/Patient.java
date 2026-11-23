package tn.sahticonnect.portail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Patient (même identité que dans dossier-cnam-service). Seules les coordonnées sont modifiables. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_patient_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 8)
    private String id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private Sexe sexe;

    private String telephone;

    private String email;

    private String adresse;

    private String pathologie;

    @ManyToOne
    @JoinColumn(name = "medecin_traitant_id")
    private Medecin medecinTraitant;

    @Embedded
    private ContactUrgence contactUrgence;
}
