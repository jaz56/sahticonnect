package tn.sahticonnect.portail.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Ordonnance (ORD-AAAA-NNNNNN). Active tant que dateFinValidite ≥ aujourd'hui. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Ordonnance extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 15)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medecin_id")
    private Medecin medecin;

    @Column(name = "date_emission", nullable = false)
    private LocalDate dateEmission;

    @Column(name = "date_fin_validite")
    private LocalDate dateFinValidite;

    private boolean renouvelable;

    @ElementCollection
    @CollectionTable(name = "ligne_ordonnance", joinColumns = @JoinColumn(name = "ordonnance_id"))
    @OrderColumn(name = "rang")
    private List<LigneOrdonnance> lignes = new ArrayList<>();

    private String remarques;
}
