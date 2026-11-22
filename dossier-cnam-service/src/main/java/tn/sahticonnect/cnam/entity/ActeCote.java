package tn.sahticonnect.cnam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Acte coté, ex. lettre-clé K, coefficient 20 → « K 20 ». */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActeCote {

    @Enumerated(EnumType.STRING)
    @Column(name = "lettre_cle", nullable = false, length = 2)
    private LettreCle lettreCle;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal coefficient;

    private String libelle;

    /** Représentation courte, ex. « K 20 ». */
    @Override
    public String toString() {
        return lettreCle + " " + coefficient.stripTrailingZeros().toPlainString();
    }
}
