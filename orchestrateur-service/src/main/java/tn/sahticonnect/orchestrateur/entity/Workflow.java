package tn.sahticonnect.orchestrateur.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Workflow de prise en charge d'une alerte (WF-AAAA-NNNNNN). Un seul workflow par alerte :
 * la contrainte d'unicité sur alerte_id garantit l'idempotence, même si l'alerte arrive deux fois.
 * Les données produites par chaque étape (couverture, références des ressources créées) sont conservées
 * pour qu'une relance reprenne là où le workflow s'est arrêté.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_workflow_alerte", columnNames = "alerte_id"))
@Getter
@Setter
@NoArgsConstructor
public class Workflow implements Persistable<String> {

    @Id
    @Column(length = 14)
    private String id;

    // ---------- Alerte d'origine ----------
    @Column(name = "alerte_id", nullable = false, length = 15)
    private String alerteId;

    @Column(name = "patient_id", nullable = false, length = 8)
    private String patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private NiveauAlerte niveau;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_mesure", length = 20)
    private TypeMesure typeMesure;

    private double valeur;

    @Column(name = "seuil_franchi")
    private String seuilFranchi;

    @Column(name = "message_alerte", length = 500)
    private String messageAlerte;

    @Column(name = "horodatage_alerte")
    private OffsetDateTime horodatageAlerte;

    // ---------- État ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 23)
    private EtatWorkflow etat;

    @Enumerated(EnumType.STRING)
    @Column(name = "etape_en_echec", length = 23)
    private NomEtape etapeEnEchec;

    @Column(name = "en_cours")
    private boolean enCours;

    /** Numéro de l'exécution en cours ou de la dernière (1 = initiale, 2 = première relance…). */
    private int tentative;

    // ---------- Saga d'escalade ----------
    /**
     * Vrai dès que la compensation de ce workflow a commencé : l'état est alors piloté par la saga (COMPENSE, ou
     * ECHEC si une compensation a échoué) et une relance reprend les compensations manquantes.
     * Boolean (et non boolean) : colonne ajoutée à une table existante, nulle pour les anciens workflows.
     */
    @Column(name = "en_compensation")
    private Boolean enCompensation;

    /** Workflow CRITIQUE dont l'escalade a déclenché la compensation de ce workflow ELEVEE. */
    @Column(name = "compense_par", length = 14)
    private String compensePar;

    // ---------- Données produites par les étapes ----------
    @Column(name = "code_vignette", length = 14)
    private String codeVignette;

    /** null : couverture inconnue (vérification en échec). */
    private Boolean couvert;

    @Column(name = "taux_remboursement")
    private Integer tauxRemboursement;

    @Column(name = "motif_couverture")
    private String motifCouverture;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_facturation", length = 13)
    private StatutFacturation statutFacturation;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_intervention", length = 16)
    private TypeIntervention typeIntervention;

    /** Ressource REST créée : AMB-… ou RDV-… (null pour une téléconsultation de repli). */
    @Column(name = "reference_externe", length = 20)
    private String referenceExterne;

    @Column(name = "description_intervention", length = 500)
    private String descriptionIntervention;

    @Column(name = "intervention_id", length = 15)
    private String interventionId;

    @Column(name = "numero_feuille_soins", length = 14)
    private String numeroFeuilleSoins;

    @Column(name = "montant_total", precision = 12, scale = 3)
    private BigDecimal montantTotal;

    @Column(name = "part_cnam", precision = 12, scale = 3)
    private BigDecimal partCNAM;

    @Column(name = "part_patient", precision = 12, scale = 3)
    private BigDecimal partPatient;

    @Column(name = "date_creation", nullable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_mise_a_jour")
    private OffsetDateTime dateMiseAJour;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "workflow_id", nullable = false)
    @OrderBy("id")
    private List<Etape> etapes = new ArrayList<>();

    @Transient
    private boolean nouveau = true;

    @Override
    public boolean isNew() {
        return nouveau;
    }

    @PostLoad
    @PostPersist
    void marquerExistant() {
        nouveau = false;
    }

    // ===================== Étapes =====================

    public void ajouterEtape(Etape etape) {
        etapes.add(etape);
    }

    /** Dernière exécution de cette étape (toutes tentatives confondues, compensations exclues). */
    public Optional<Etape> derniere(NomEtape nom) {
        return etapes.stream().filter(e -> e.getNom() == nom && !e.estCompensation()).reduce((premiere, seconde) -> seconde);
    }

    /** L'étape a-t-elle déjà réussi ? (une relance ne la rejoue pas) */
    public boolean estReussie(NomEtape nom) {
        return derniere(nom).map(e -> e.getStatut() == StatutEtape.OK).orElse(false);
    }

    /** Dernière compensation de cette étape. */
    public Optional<Etape> derniereCompensation(NomEtape nom) {
        return etapes.stream().filter(e -> e.getNom() == nom && e.estCompensation()).reduce((premiere, seconde) -> seconde);
    }

    /** La compensation de cette étape est-elle faite ? (OK, ou IGNOREE : rien à compenser) : jamais rejouée. */
    public boolean estCompensee(NomEtape nom) {
        return derniereCompensation(nom).map(e -> e.getStatut() != StatutEtape.ECHEC).orElse(false);
    }

    public boolean estEnCompensation() {
        return Boolean.TRUE.equals(enCompensation);
    }

    /**
     * Recalcule l'état : FACTUREE si les 5 étapes (et ESCALADE, si elle a eu lieu) sont réussies ; sinon, en cours
     * d'exécution, la dernière étape principale réussie ; à la fin d'une exécution incomplète, ECHEC avec la première
     * étape non réussie. Un workflow en compensation garde l'état fixé par la saga (COMPENSE ou ECHEC).
     */
    public void recalculerEtat(boolean executionTerminee) {
        if (estEnCompensation()) {
            return;
        }
        List<NomEtape> attendues = new ArrayList<>(NomEtape.PRINCIPALES);
        if (derniere(NomEtape.ESCALADE).isPresent()) {
            attendues.add(2, NomEtape.ESCALADE);
        }
        NomEtape premiereNonReussie = null;
        NomEtape derniereReussie = null;
        for (NomEtape nom : attendues) {
            if (estReussie(nom)) {
                if (nom != NomEtape.ESCALADE) {
                    derniereReussie = nom;
                }
            } else if (premiereNonReussie == null && derniere(nom).isPresent()) {
                premiereNonReussie = nom;
            }
        }
        boolean toutesReussies = attendues.stream().allMatch(this::estReussie);
        if (toutesReussies) {
            etat = EtatWorkflow.FACTUREE;
            etapeEnEchec = null;
        } else if (executionTerminee) {
            etat = EtatWorkflow.ECHEC;
            etapeEnEchec = premiereNonReussie != null ? premiereNonReussie : NomEtape.ALERTE_RECUE;
        } else {
            etat = derniereReussie != null ? EtatWorkflow.apres(derniereReussie) : EtatWorkflow.ALERTE_RECUE;
            etapeEnEchec = premiereNonReussie;
        }
    }
}
