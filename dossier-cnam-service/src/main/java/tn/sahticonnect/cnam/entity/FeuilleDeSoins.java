package tn.sahticonnect.cnam.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Feuille de soins émise pour un assuré (numéro FS-AAAA-NNNNNN). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class FeuilleDeSoins {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String numero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "identifiant_patient")
    private Assure assure;

    @Column(name = "date_soins", nullable = false)
    private LocalDate dateSoins;

    @Column(nullable = false)
    private String etablissement;

    @Column(nullable = false)
    private String medecin;

    @OneToMany(mappedBy = "feuille", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneActe> lignes = new ArrayList<>();

    @Column(name = "montant_total", nullable = false, precision = 12, scale = 3)
    private BigDecimal montantTotal;

    @Column(name = "taux_applique", nullable = false)
    private int tauxApplique;

    @Column(name = "part_cnam", nullable = false, precision = 12, scale = 3)
    private BigDecimal partCNAM;

    @Column(name = "part_patient", nullable = false, precision = 12, scale = 3)
    private BigDecimal partPatient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private StatutFeuille statut;

    @Column(name = "code_certification", nullable = false, length = 64)
    private String codeCertification;

    @Column(name = "reference_intervention")
    private String referenceIntervention;

    /** Ajoute une ligne en maintenant la relation bidirectionnelle. */
    public void ajouterLigne(LigneActe ligne) {
        ligne.setFeuille(this);
        lignes.add(ligne);
    }
}
