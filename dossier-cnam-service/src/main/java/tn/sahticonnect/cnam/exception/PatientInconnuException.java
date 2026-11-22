package tn.sahticonnect.cnam.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/** Aucun assuré ne correspond à l'identifiant patient → SOAP Fault « Client ». */
@SoapFault(faultCode = FaultCode.CLIENT, locale = "fr")
public class PatientInconnuException extends RuntimeException {

    public PatientInconnuException(String identifiantPatient) {
        super("Patient inconnu : " + identifiantPatient);
    }
}
