package tn.sahticonnect.cnam.exception;

/** Aucune feuille de soins ne porte ce numéro → fault Client, code FEUILLE_INCONNUE. */
public class FeuilleInconnueException extends CnamException {

    public static final String CODE = "FEUILLE_INCONNUE";

    public FeuilleInconnueException(String numero) {
        super(CODE, "Feuille de soins inconnue : " + numero);
    }
}
