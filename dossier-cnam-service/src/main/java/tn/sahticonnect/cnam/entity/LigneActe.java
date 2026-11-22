package tn.sahticonnect.cnam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Ligne d'une feuille de soins : un acte coté et son montant. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class LigneActe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feuille_id")
    private FeuilleDeSoins feuille;

    @Embedded
    private ActeCote acte;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal montant;

    public LigneActe(ActeCote acte, BigDecimal montant) {
        this.acte = acte;
        this.montant = montant;
    }
}
