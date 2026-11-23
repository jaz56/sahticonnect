package tn.sahticonnect.cnam.exception;

/** Aucun assuré ne correspond à l'identifiant patient → fault Client, code PATIENT_INCONNU. */
public class PatientInconnuException extends CnamException {

    public static final String CODE = "PATIENT_INCONNU";

    public PatientInconnuException(String identifiantPatient) {
        super(CODE, "Patient inconnu : " + identifiantPatient);
    }
}
