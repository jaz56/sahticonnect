package tn.sahticonnect.orchestrateur.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.xml.bind.JAXBElement;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import tn.sahticonnect.orchestrateur.client.cnam.ws.ActeCote;
import tn.sahticonnect.orchestrateur.client.cnam.ws.ConsulterHistoriqueRequest;
import tn.sahticonnect.orchestrateur.client.cnam.ws.ConsulterHistoriqueResponse;
import tn.sahticonnect.orchestrateur.client.cnam.ws.EmettreFeuilleDeSoinsRequest;
import tn.sahticonnect.orchestrateur.client.cnam.ws.EmettreFeuilleDeSoinsResponse;
import tn.sahticonnect.orchestrateur.client.cnam.ws.ErreurCnam;
import tn.sahticonnect.orchestrateur.client.cnam.ws.FeuilleDeSoins;
import tn.sahticonnect.orchestrateur.client.cnam.ws.LettreCle;
import tn.sahticonnect.orchestrateur.client.cnam.ws.VerifierDroitsCNAMRequest;
import tn.sahticonnect.orchestrateur.client.cnam.ws.VerifierDroitsCNAMResponse;
import tn.sahticonnect.orchestrateur.config.OrchestrateurProperties;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/**
 * Client SOAP de dossier-cnam-service : WebServiceTemplate + classes JAXB générées depuis le XSD.
 * <ul>
 *   <li>SOAP Fault « Client » → ErreurMetierException avec le code du &lt;detail&gt; (PATIENT_INCONNU,
 *       VIGNETTE_INVALIDE, ACTE_INVALIDE) : jamais réessayée ;</li>
 *   <li>panne réseau, délai de 3 s, fault « Server », HTTP 5xx → erreur technique : retry puis circuit breaker.</li>
 * </ul>
 */
@Component
public class CnamClient {

    public static final String SERVICE = "dossier-cnam";

    /** Acte coté, ex. V 1, K 30. */
    public record Acte(String lettreCle, BigDecimal coefficient) {

        public static Acte de(String lettreCle, int coefficient) {
            return new Acte(lettreCle, BigDecimal.valueOf(coefficient));
        }

        @Override
        public String toString() {
            return lettreCle + " " + coefficient.stripTrailingZeros().toPlainString();
        }
    }

    public record Couverture(boolean couvert, int tauxRemboursement, String motif) {
    }

    public record FeuilleEmise(String numero, BigDecimal montantTotal, BigDecimal partCNAM, BigDecimal partPatient,
                               String statut) {

        public boolean estCertifiee() {
            return "CERTIFIEE".equals(statut);
        }
    }

    private final WebServiceTemplate template;
    private final Jaxb2Marshaller marshaller;
    private final DatatypeFactory datatypeFactory;

    public CnamClient(OrchestrateurProperties proprietes) {
        this.marshaller = new Jaxb2Marshaller();
        this.marshaller.setContextPath(ConsulterHistoriqueRequest.class.getPackageName());
        HttpUrlConnectionMessageSender transport = new HttpUrlConnectionMessageSender();
        transport.setConnectionTimeout(proprietes.timeout());
        transport.setReadTimeout(proprietes.timeout());
        this.template = new WebServiceTemplate(marshaller);
        this.template.setDefaultUri(proprietes.cnam().url());
        this.template.setMessageSender(transport);
        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** consulterHistorique : sert à récupérer la vignette de l'assuré. */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-idempotent")
    public String consulterVignette(String patientId) {
        ConsulterHistoriqueRequest requete = new ConsulterHistoriqueRequest();
        requete.setIdentifiantPatient(patientId);
        ConsulterHistoriqueResponse reponse = (ConsulterHistoriqueResponse) appeler(requete);
        return reponse.getAssure().getCodeVignetteCNAM();
    }

    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-idempotent")
    public Couverture verifierDroits(String patientId, String codeVignette, Acte acte) {
        VerifierDroitsCNAMRequest requete = new VerifierDroitsCNAMRequest();
        requete.setIdentifiantPatient(patientId);
        requete.setCodeVignetteCNAM(codeVignette);
        requete.setActe(versXml(acte));
        VerifierDroitsCNAMResponse reponse = (VerifierDroitsCNAMResponse) appeler(requete);
        return new Couverture(reponse.getCouverture().isCouvert(), reponse.getCouverture().getTauxRemboursement(),
                reponse.getCouverture().getMotif());
    }

    /**
     * emettreFeuilleDeSoins : création, NON idempotente côté CNAM (chaque appel crée une feuille). Réessayée
     * seulement si la requête n'a pas pu partir (retry « appel-non-idempotent »).
     */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-non-idempotent")
    public FeuilleEmise emettreFeuilleDeSoins(String patientId, String codeVignette, LocalDate dateSoins,
                                             String etablissement, String medecin, List<Acte> actes,
                                             String referenceIntervention) {
        EmettreFeuilleDeSoinsRequest requete = new EmettreFeuilleDeSoinsRequest();
        requete.setIdentifiantPatient(patientId);
        requete.setCodeVignetteCNAM(codeVignette);
        requete.setDateSoins(datatypeFactory.newXMLGregorianCalendarDate(dateSoins.getYear(), dateSoins.getMonthValue(),
                dateSoins.getDayOfMonth(), DatatypeConstants.FIELD_UNDEFINED));
        requete.setEtablissement(etablissement);
        requete.setMedecin(medecin);
        actes.forEach(a -> requete.getActes().add(versXml(a)));
        requete.setReferenceIntervention(referenceIntervention);
        FeuilleDeSoins feuille = ((EmettreFeuilleDeSoinsResponse) appeler(requete)).getFeuilleDeSoins();
        return new FeuilleEmise(feuille.getNumero(), feuille.getMontantTotal(), feuille.getPartCNAM(),
                feuille.getPartPatient(), feuille.getStatut().value());
    }

    private Object appeler(Object requete) {
        try {
            return template.marshalSendAndReceive(requete);
        } catch (SoapFaultClientException e) {
            SoapFault fault = e.getSoapFault();
            if (fault != null && "Client".equals(fault.getFaultCode().getLocalPart())) {
                throw new ErreurMetierException(SERVICE, codeDuDetail(fault), e.getFaultStringOrReason());
            }
            // fault « Server » : panne côté service, réessayable
            throw new ReponseAbsenteException(SERVICE, "fault Server « " + e.getFaultStringOrReason() + " »", e);
        } catch (RuntimeException e) {
            throw Pannes.technique(SERVICE, e);
        }
    }

    /** Code stable du &lt;detail&gt; (élément ErreurCnam de l'opération) ; « FAULT_CLIENT » s'il est absent. */
    private String codeDuDetail(SoapFault fault) {
        SoapFaultDetail detail = fault.getFaultDetail();
        if (detail == null) {
            return "FAULT_CLIENT"; // ex. requête rejetée par la validation XSD
        }
        Iterator<SoapFaultDetailElement> elements = detail.getDetailEntries();
        while (elements.hasNext()) {
            Object contenu = marshaller.unmarshal(elements.next().getSource());
            if (contenu instanceof JAXBElement<?> element && element.getValue() instanceof ErreurCnam erreur) {
                return erreur.getCode();
            }
        }
        return "FAULT_CLIENT";
    }

    private static ActeCote versXml(Acte acte) {
        ActeCote xml = new ActeCote();
        xml.setLettreCle(LettreCle.fromValue(acte.lettreCle()));
        xml.setCoefficient(acte.coefficient());
        return xml;
    }
}
