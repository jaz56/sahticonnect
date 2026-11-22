package tn.sahticonnect.cnam.service;

import org.springframework.stereotype.Service;
import tn.sahticonnect.cnam.entity.ActeCote;
import tn.sahticonnect.cnam.entity.Assure;
import tn.sahticonnect.cnam.entity.Filiere;
import tn.sahticonnect.cnam.exception.ActeInvalideException;
import tn.sahticonnect.cnam.service.model.Montants;
import tn.sahticonnect.cnam.service.model.ResultatCouverture;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Règles de tarification CNAM (tous les calculs en BigDecimal, 3 décimales, arrondi HALF_UP).
 * <ul>
 *   <li>montant d'un acte = valeur de la lettre-clé × coefficient ;</li>
 *   <li>taux : APCI → 100 %, sinon filière PUBLIQUE → 80 %, sinon 50 % ;</li>
 *   <li>vignette expirée à la date de l'acte → non couvert, taux 0.</li>
 * </ul>
 * Service sans état et sans dépendance : testable unitairement avec {@code new}.
 */
@Service
public class TarificationService {

    public static final int ECHELLE = 3;
    public static final RoundingMode ARRONDI = RoundingMode.HALF_UP;

    public static final int TAUX_APCI = 100;
    public static final int TAUX_FILIERE_PUBLIQUE = 80;
    public static final int TAUX_AUTRES = 50;

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Vérifie qu'un acte est exploitable (lettre-clé présente, coefficient strictement positif). */
    public void verifierActe(ActeCote acte) {
        if (acte == null || acte.getLettreCle() == null) {
            throw new ActeInvalideException("Acte invalide : lettre-clé manquante");
        }
        if (acte.getCoefficient() == null || acte.getCoefficient().signum() <= 0) {
            throw new ActeInvalideException("Acte invalide : le coefficient de " + acte.getLettreCle()
                    + " doit être strictement positif (reçu : "
                    + (acte.getCoefficient() == null ? "aucun" : acte.getCoefficient().toPlainString()) + ")");
        }
    }

    /** Montant d'un acte : valeur de la lettre-clé × coefficient, arrondi à 3 décimales. */
    public BigDecimal montantActe(ActeCote acte) {
        verifierActe(acte);
        return arrondir(acte.getLettreCle().getValeur().multiply(acte.getCoefficient()));
    }

    /** Somme des montants des actes (chaque acte est arrondi avant la somme). */
    public BigDecimal montantTotal(List<ActeCote> actes) {
        if (actes == null || actes.isEmpty()) {
            throw new ActeInvalideException("Acte invalide : au moins un acte est requis");
        }
        return actes.stream()
                .map(this::montantActe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ECHELLE, ARRONDI);
    }

    /** Taux de remboursement hors question de validité : APCI 100, sinon PUBLIQUE 80, sinon 50. */
    public int tauxApplicable(boolean apci, Filiere filiere) {
        if (apci) {
            return TAUX_APCI;
        }
        return filiere == Filiere.PUBLIQUE ? TAUX_FILIERE_PUBLIQUE : TAUX_AUTRES;
    }

    /** Couverture de l'assuré à la date de l'acte (vignette expirée si dateFinValidite &lt; dateActe). */
    public ResultatCouverture evaluerCouverture(Assure assure, LocalDate dateActe) {
        if (assure.getDateFinValidite().isBefore(dateActe)) {
            return new ResultatCouverture(false, 0,
                    "Vignette expirée le " + assure.getDateFinValidite().format(FORMAT_DATE));
        }
        int taux = tauxApplicable(assure.isApci(), assure.getFiliere());
        return new ResultatCouverture(true, taux, motif(assure, taux));
    }

    /** Répartit un total : part CNAM = total × taux / 100 (arrondie), part patient = reste. */
    public Montants repartir(BigDecimal total, int taux) {
        BigDecimal totalArrondi = arrondir(total);
        BigDecimal partCNAM = arrondir(totalArrondi.multiply(BigDecimal.valueOf(taux)).divide(CENT));
        BigDecimal partPatient = totalArrondi.subtract(partCNAM).setScale(ECHELLE, ARRONDI);
        return new Montants(totalArrondi, partCNAM, partPatient);
    }

    public static BigDecimal arrondir(BigDecimal montant) {
        return montant.setScale(ECHELLE, ARRONDI);
    }

    private String motif(Assure assure, int taux) {
        if (assure.isApci()) {
            return "APCI " + taux + " %";
        }
        return switch (assure.getFiliere()) {
            case PUBLIQUE -> "Filière publique " + taux + " %";
            case PRIVEE -> "Filière privée " + taux + " %";
            case REMBOURSEMENT -> "Remboursement " + taux + " %";
        };
    }
}
