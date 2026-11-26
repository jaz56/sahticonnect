package tn.sahticonnect.orchestrateur.correlation;

import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Appels SOAP sortants (WebServiceTemplate) : ajoute l'en-tête HTTP {@value Correlation#ENTETE} avec l'identifiant
 * du traitement en cours. Spring WS ouvre la connexion HTTP avant d'appeler les intercepteurs : on y ajoute l'en-tête.
 * (En-tête HTTP et non en-tête SOAP : le contrat WSDL n'est pas modifié, et la gateway le lit comme pour REST.)
 */
public class IntercepteurCorrelationSoap implements ClientInterceptor {

    @Override
    public boolean handleRequest(MessageContext contexte) throws WebServiceClientException {
        String id = Correlation.courant();
        TransportContext transport = TransportContextHolder.getTransportContext();
        if (id != null && transport != null
                && transport.getConnection() instanceof HeadersAwareSenderWebServiceConnection connexion) {
            try {
                connexion.addRequestHeader(Correlation.ENTETE, id);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext contexte) {
        return true;
    }

    @Override
    public boolean handleFault(MessageContext contexte) {
        return true;
    }

    @Override
    public void afterCompletion(MessageContext contexte, Exception exception) {
        // rien
    }
}
