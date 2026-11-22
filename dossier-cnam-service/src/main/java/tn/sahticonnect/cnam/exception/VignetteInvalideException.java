package tn.sahticonnect.cnam.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/** La vignette fournie ne correspond pas au patient → SOAP Fault « Client ». */
@SoapFault(faultCode = FaultCode.CLIENT, locale = "fr")
public class VignetteInvalideException extends RuntimeException {

    public VignetteInvalideException(String codeVignette, String identifiantPatient) {
        super("La vignette " + codeVignette + " ne correspond pas au patient " + identifiantPatient);
    }
}
