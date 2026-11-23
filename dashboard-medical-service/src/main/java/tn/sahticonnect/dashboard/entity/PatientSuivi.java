package tn.sahticonnect.dashboard.entity;

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

/**
 * Patient suivi par le dashboard (mêmes identités que dans dossier-cnam-service et portail-patient-service).
 * Les champs distants du type GraphQL (constantes, couvert, tauxRemboursement) ne sont pas stockés :
 * ils sont résolus à la demande.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientSuivi extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 8)
    private String id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(nullable = false)
    private String service;

    @Column(nullable = false)
    private String pathologie;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_risque", nullable = false, length = 8)
    private NiveauRisque niveauRisque;

    @Column(name = "medecin_referent")
    private String medecinReferent;
}
