package tn.sahticonnect.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Traitement en cours ou passé (cohérent avec les ordonnances de portail-patient-service). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Traitement extends EntiteAIdentifiantMetier {

    @Id
    @Column(length = 8)
    private String id;

    @Column(name = "patient_id", nullable = false, length = 8)
    private String patientId;

    @Column(nullable = false)
    private String medicament;

    @Column(nullable = false)
    private String posologie;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    private String prescripteur;

    /** Actif à cette date : commencé et pas encore terminé. */
    public boolean estActifLe(LocalDate date) {
        return !dateDebut.isAfter(date) && (dateFin == null || !dateFin.isBefore(date));
    }
}
