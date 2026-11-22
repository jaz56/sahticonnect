package tn.sahticonnect.cnam.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/** Entrée de l'historique de soins d'un assuré. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class EntreeHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "identifiant_patient")
    private Assure assure;

    // « date » est un mot réservé en SQL : colonne renommée
    @Column(name = "date_soins", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String etablissement;

    @Column(nullable = false)
    private String diagnostic;

    @ElementCollection
    @CollectionTable(name = "entree_historique_acte", joinColumns = @JoinColumn(name = "entree_id"))
    @OrderColumn(name = "rang")
    private List<ActeCote> actes = new ArrayList<>();

    @Column(name = "numero_feuille_soins", length = 14)
    private String numeroFeuilleSoins;
}
