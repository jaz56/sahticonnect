package tn.sahticonnect.cnam.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/** Acte coté invalide (coefficient absent ou ≤ 0) → SOAP Fault « Client ». */
@SoapFault(faultCode = FaultCode.CLIENT, locale = "fr")
public class ActeInvalideException extends RuntimeException {

    public ActeInvalideException(String message) {
        super(message);
    }
}
