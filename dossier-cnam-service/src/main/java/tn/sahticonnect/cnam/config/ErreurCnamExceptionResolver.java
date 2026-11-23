package tn.sahticonnect.cnam.config;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.server.endpoint.AbstractSoapFaultDefinitionExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import tn.sahticonnect.cnam.exception.CnamException;
import tn.sahticonnect.cnam.ws.ErreurCnam;

import javax.xml.namespace.QName;
import java.util.Locale;

import static tn.sahticonnect.cnam.config.WebServiceConfig.NAMESPACE;

/**
 * Transforme une {@link CnamException} en SOAP Fault « Client » dont le &lt;detail&gt; contient
 * l'élément déclaré dans le XSD pour l'opération appelée, par exemple :
 * <pre>
 * &lt;detail&gt;
 *   &lt;cnam:verifierDroitsCNAMFault&gt;
 *     &lt;cnam:code&gt;VIGNETTE_INVALIDE&lt;/cnam:code&gt;
 *     &lt;cnam:message&gt;La vignette … ne correspond pas au patient …&lt;/cnam:message&gt;
 *   &lt;/cnam:verifierDroitsCNAMFault&gt;
 * &lt;/detail&gt;
 * </pre>
 * Le nom de l'élément est déduit de la méthode de l'endpoint : verifierDroitsCNAM → verifierDroitsCNAMFault.
 * Les autres exceptions sont laissées aux résolveurs par défaut de Spring WS (fault Server).
 */
@Component
public class ErreurCnamExceptionResolver extends AbstractSoapFaultDefinitionExceptionResolver {

    private static final Locale FRANCAIS = Locale.FRENCH;

    private final JAXBContext contexteJaxb;

    public ErreurCnamExceptionResolver() throws JAXBException {
        this.contexteJaxb = JAXBContext.newInstance(ErreurCnam.class);
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception ex) {
        if (!(ex instanceof CnamException)) {
            return null;
        }
        SoapFaultDefinition definition = new SoapFaultDefinition();
        definition.setFaultCode(SoapFaultDefinition.CLIENT);
        definition.setFaultStringOrReason(ex.getMessage());
        definition.setLocale(FRANCAIS);
        return definition;
    }

    @Override
    protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
        CnamException erreur = (CnamException) ex;
        ErreurCnam detail = new ErreurCnam();
        detail.setCode(erreur.getCode());
        detail.setMessage(erreur.getMessage());

        QName element = new QName(NAMESPACE, nomOperation(endpoint) + "Fault");
        try {
            Marshaller marshaller = contexteJaxb.createMarshaller(); // non thread-safe : un par appel
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            marshaller.marshal(new JAXBElement<>(element, ErreurCnam.class, detail),
                    fault.addFaultDetail().getResult());
        } catch (JAXBException e) {
            throw new IllegalStateException("Impossible d'écrire le détail de la fault", e);
        }
    }

    /** Nom de l'opération = nom de la méthode @PayloadRoot de l'endpoint. */
    private static String nomOperation(Object endpoint) {
        if (endpoint instanceof MethodEndpoint methode) {
            return methode.getMethod().getName();
        }
        throw new IllegalStateException("Endpoint inattendu : " + endpoint);
    }
}
