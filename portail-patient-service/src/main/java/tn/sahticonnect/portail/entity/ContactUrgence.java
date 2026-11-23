package tn.sahticonnect.portail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Personne à prévenir en cas d'urgence (intégrée à la table patient). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactUrgence {

    @Column(name = "contact_urgence_nom")
    private String nom;

    @Column(name = "contact_urgence_lien")
    private String lien;

    @Column(name = "contact_urgence_telephone")
    private String telephone;
}
