package tn.sahticonnect.orchestrateur.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Trace d'une étape exécutée : nom, statut, fin, durée, message, numéro de tentative. */
@Entity
@Table(name = "etape_workflow")
@Getter
@Setter
@NoArgsConstructor
public class Etape {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 23)
    private NomEtape nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private StatutEtape statut;

    /** Fin de l'étape. */
    @Column(nullable = false)
    private OffsetDateTime horodatage;

    @Column(name = "duree_ms", nullable = false)
    private long dureeMs;

    /** 1 pour l'exécution initiale, 2 pour la première relance… */
    @Column(nullable = false)
    private int tentative;

    @Column(length = 1000)
    private String message;

    public Etape(NomEtape nom, StatutEtape statut, OffsetDateTime horodatage, long dureeMs, int tentative, String message) {
        this.nom = nom;
        this.statut = statut;
        this.horodatage = horodatage;
        this.dureeMs = dureeMs;
        this.tentative = tentative;
        this.message = message;
    }
}
