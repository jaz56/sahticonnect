package tn.sahticonnect.cnam.service;

import tn.sahticonnect.cnam.exception.FeuilleInconnueException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.cnam.entity.ActeCote;
import tn.sahticonnect.cnam.entity.Assure;
import tn.sahticonnect.cnam.entity.EntreeHistorique;
import tn.sahticonnect.cnam.entity.FeuilleDeSoins;
import tn.sahticonnect.cnam.entity.LigneActe;
import tn.sahticonnect.cnam.entity.StatutFeuille;
import tn.sahticonnect.cnam.exception.PatientInconnuException;
import tn.sahticonnect.cnam.exception.VignetteInvalideException;
import tn.sahticonnect.cnam.repository.AssureRepository;
import tn.sahticonnect.cnam.repository.EntreeHistoriqueRepository;
import tn.sahticonnect.cnam.repository.FeuilleDeSoinsRepository;
import tn.sahticonnect.cnam.repository.NumerotationRepository;
import tn.sahticonnect.cnam.service.model.ComparaisonGaranties;
import tn.sahticonnect.cnam.service.model.ConsultationHistorique;
import tn.sahticonnect.cnam.service.model.DemandeFeuilleDeSoins;
import tn.sahticonnect.cnam.service.model.Montants;
import tn.sahticonnect.cnam.service.model.OffreSoins;
import tn.sahticonnect.cnam.service.model.ResultatCouverture;
import tn.sahticonnect.cnam.service.model.ResultatGarantie;
import tn.sahticonnect.cnam.service.model.VerificationDroits;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/** Logique métier des 4 opérations SOAP du dossier CNAM. */
@Service
@RequiredArgsConstructor
@Transactional
public class DossierCnamService {

    /** Bornes utilisées quand le filtre de dates de l'historique est absent. */
    private static final LocalDate DEBUT_PAR_DEFAUT = LocalDate.of(1900, 1, 1);
    private static final LocalDate FIN_PAR_DEFAUT = LocalDate.of(9999, 12, 31);

    /** Préfixe de la recommandation de comparerGaranties quand la vignette est expirée. */
    public static final String PREFIXE_SIMULATION = "Simulation (vignette expirée) : ";

    private final AssureRepository assureRepository;
    private final FeuilleDeSoinsRepository feuilleRepository;
    private final EntreeHistoriqueRepository historiqueRepository;
    private final NumerotationRepository numerotation;
    private final TarificationService tarification;
    private final Clock horloge;

    // ===================== verifierDroitsCNAM =====================

    @Transactional(readOnly = true)
    public VerificationDroits verifierDroits(String identifiantPatient, String codeVignette,
                                             ActeCote acte, LocalDate dateActe) {
        Assure assure = chargerAssure(identifiantPatient, codeVignette);
        LocalDate date = dateActe != null ? dateActe : LocalDate.now(horloge);
        ResultatCouverture couverture = tarification.evaluerCouverture(assure, date);
        Montants montants = tarification.repartir(tarification.montantActe(acte), couverture.taux());
        return new VerificationDroits(assure, couverture, montants);
    }

    // ===================== emettreFeuilleDeSoins =====================

    /**
     * Émet, certifie (si couvert) et enregistre une feuille de soins, puis l'ajoute à l'historique.
     * Patient non couvert → statut EMISE et taux 0 (cas « à régulariser »).
     */
    public FeuilleDeSoins emettreFeuilleDeSoins(DemandeFeuilleDeSoins demande) {
        Assure assure = chargerAssure(demande.identifiantPatient(), demande.codeVignetteCNAM());
        ResultatCouverture couverture = tarification.evaluerCouverture(assure, demande.dateSoins());

        FeuilleDeSoins feuille = new FeuilleDeSoins();
        feuille.setAssure(assure);
        feuille.setDateSoins(demande.dateSoins());
        feuille.setEtablissement(demande.etablissement());
        feuille.setMedecin(demande.medecin());
        feuille.setReferenceIntervention(demande.referenceIntervention());
        for (ActeCote acte : demande.actes()) {
            feuille.ajouterLigne(new LigneActe(acte, tarification.montantActe(acte)));
        }

        BigDecimal total = tarification.montantTotal(demande.actes());
        Montants montants = tarification.repartir(total, couverture.taux());
        feuille.setMontantTotal(montants.total());
        feuille.setTauxApplique(couverture.taux());
        feuille.setPartCNAM(montants.partCNAM());
        feuille.setPartPatient(montants.partPatient());
        feuille.setStatut(couverture.couvert() ? StatutFeuille.CERTIFIEE : StatutFeuille.EMISE);
        // numéro tiré de la séquence de base, avant l'enregistrement
        feuille.setNumero(numero(demande.dateSoins(), numerotation.prochaineValeurFeuille()));
        feuille.setCodeCertification(codeCertification(feuille));
        feuilleRepository.save(feuille);

        historiqueRepository.save(entreeHistorique(feuille, demande.actes()));
        return feuille;
    }

    // ===================== annulerFeuilleDeSoins =====================

    /**
     * Annule une feuille de soins (compensation d'une saga) et l'inscrit dans l'historique du patient.
     * Idempotent : une feuille déjà ANNULEE est renvoyée telle quelle, sans nouvelle ligne d'historique.
     */
    public FeuilleDeSoins annulerFeuilleDeSoins(String numero, String motif) {
        FeuilleDeSoins feuille = feuilleRepository.findByNumero(numero)
                .orElseThrow(() -> new FeuilleInconnueException(numero));
        feuille.getLignes().size(); // lignes chargées dans la transaction : le mapper les lit ensuite
        if (feuille.getStatut() == StatutFeuille.ANNULEE) {
            return feuille;
        }
        feuille.setStatut(StatutFeuille.ANNULEE);

        EntreeHistorique entree = new EntreeHistorique();
        entree.setAssure(feuille.getAssure());
        entree.setDate(LocalDate.now(horloge));
        entree.setEtablissement(feuille.getEtablissement());
        entree.setDiagnostic("Annulation de la feuille " + numero + " : " + motif);
        List<ActeCote> actes = new ArrayList<>();
        for (LigneActe ligne : feuille.getLignes()) {
            ActeCote acte = ligne.getActe();
            actes.add(new ActeCote(acte.getLettreCle(), acte.getCoefficient(), acte.getLibelle()));
        }
        entree.setActes(actes);
        entree.setNumeroFeuilleSoins(numero);
        historiqueRepository.save(entree);
        return feuille;
    }

    // ===================== consulterHistorique =====================

    @Transactional(readOnly = true)
    public ConsultationHistorique consulterHistorique(String identifiantPatient,
                                                      LocalDate dateDebut, LocalDate dateFin) {
        Assure assure = assureRepository.findById(identifiantPatient)
                .orElseThrow(() -> new PatientInconnuException(identifiantPatient));
        List<EntreeHistorique> entrees = historiqueRepository
                .findByAssureIdentifiantPatientAndDateBetweenOrderByDateDescIdDesc(
                        identifiantPatient,
                        dateDebut != null ? dateDebut : DEBUT_PAR_DEFAUT,
                        dateFin != null ? dateFin : FIN_PAR_DEFAUT);
        return new ConsultationHistorique(assure, entrees);
    }

    // ===================== comparerGaranties =====================

    @Transactional(readOnly = true)
    public ComparaisonGaranties comparerGaranties(String identifiantPatient, OffreSoins offreA, OffreSoins offreB) {
        Assure assure = assureRepository.findById(identifiantPatient)
                .orElseThrow(() -> new PatientInconnuException(identifiantPatient));
        ResultatGarantie a = calculerGarantie(assure, offreA);
        ResultatGarantie b = calculerGarantie(assure, offreB);

        BigDecimal patientA = a.montants().partPatient();
        BigDecimal patientB = b.montants().partPatient();
        BigDecimal difference = patientA.subtract(patientB).abs();
        int comparaison = patientA.compareTo(patientB);
        String recommandation = comparaison == 0 ? "Équivalent" : (comparaison < 0 ? a.libelle() : b.libelle());
        // vignette expirée aujourd'hui : le calcul reste indicatif (aucune prise en charge réelle)
        if (!tarification.evaluerCouverture(assure, LocalDate.now(horloge)).couvert()) {
            recommandation = PREFIXE_SIMULATION + recommandation;
        }
        return new ComparaisonGaranties(a, b, difference, recommandation);
    }

    // ===================== Méthodes internes =====================

    /** Charge l'assuré et vérifie que la vignette lui appartient. */
    private Assure chargerAssure(String identifiantPatient, String codeVignette) {
        Assure assure = assureRepository.findById(identifiantPatient)
                .orElseThrow(() -> new PatientInconnuException(identifiantPatient));
        if (!assure.getCodeVignetteCNAM().equals(codeVignette)) {
            throw new VignetteInvalideException(codeVignette, identifiantPatient);
        }
        return assure;
    }

    /** Taux de la filière proposée, l'APCI du patient restant prise en compte. */
    private ResultatGarantie calculerGarantie(Assure assure, OffreSoins offre) {
        int taux = tarification.tauxApplicable(assure.isApci(), offre.filiere());
        Montants montants = tarification.repartir(tarification.montantTotal(offre.actes()), taux);
        return new ResultatGarantie(offre.libelle(), taux, montants);
    }

    /** Numéro FS-AAAA-NNNNNN : année des soins + valeur de la séquence feuille_soins_seq. */
    static String numero(LocalDate dateSoins, long valeurSequence) {
        return "FS-%d-%06d".formatted(dateSoins.getYear(), valeurSequence);
    }

    /** Empreinte SHA-256 (hex) de numéro + patient + date + actes + montant (certification simulée). */
    public static String codeCertification(FeuilleDeSoins feuille) {
        String actes = feuille.getLignes().stream()
                .map(ligne -> ligne.getActe().toString())
                .collect(Collectors.joining(","));
        String contenu = String.join("|",
                feuille.getNumero(),
                feuille.getAssure().getIdentifiantPatient(),
                feuille.getDateSoins().toString(),
                actes,
                feuille.getMontantTotal().toPlainString());
        try {
            byte[] empreinte = MessageDigest.getInstance("SHA-256").digest(contenu.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(empreinte);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private EntreeHistorique entreeHistorique(FeuilleDeSoins feuille, List<ActeCote> actes) {
        EntreeHistorique entree = new EntreeHistorique();
        entree.setAssure(feuille.getAssure());
        entree.setDate(feuille.getDateSoins());
        entree.setEtablissement(feuille.getEtablissement());
        entree.setDiagnostic(feuille.getReferenceIntervention() != null
                ? "Prise en charge suite à l'intervention " + feuille.getReferenceIntervention()
                : "Soins (" + feuille.getMedecin() + ")");
        // copie des actes : la collection embarquée de l'historique est indépendante des lignes
        List<ActeCote> copie = new ArrayList<>();
        for (ActeCote acte : actes) {
            copie.add(new ActeCote(acte.getLettreCle(), acte.getCoefficient(), acte.getLibelle()));
        }
        entree.setActes(copie);
        entree.setNumeroFeuilleSoins(feuille.getNumero());
        return entree;
    }
}
