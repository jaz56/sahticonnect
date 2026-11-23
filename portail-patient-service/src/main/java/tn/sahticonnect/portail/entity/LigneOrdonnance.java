package tn.sahticonnect.portail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ligne d'ordonnance : médicament, posologie, durée. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LigneOrdonnance {

    @Column(nullable = false)
    private String medicament;

    @Column(nullable = false)
    private String posologie;

    @Column(name = "duree_jours")
    private Integer dureeJours;
}
