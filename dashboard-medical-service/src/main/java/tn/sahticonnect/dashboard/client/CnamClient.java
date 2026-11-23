package tn.sahticonnect.dashboard.client;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import tn.sahticonnect.dashboard.client.ModelesDistants.ActeCote;
import tn.sahticonnect.dashboard.client.ModelesDistants.Couverture;
import tn.sahticonnect.dashboard.client.ModelesDistants.EntreeHistoriqueSoins;
import tn.sahticonnect.dashboard.client.ModelesDistants.HistoriqueCnam;
import tn.sahticonnect.dashboard.client.cnam.ws.ConsulterHistoriqueRequest;
import tn.sahticonnect.dashboard.client.cnam.ws.ConsulterHistoriqueResponse;
import tn.sahticonnect.dashboard.client.cnam.ws.LettreCle;
import tn.sahticonnect.dashboard.client.cnam.ws.VerifierDroitsCNAMRequest;
import tn.sahticonnect.dashboard.client.cnam.ws.VerifierDroitsCNAMResponse;
import tn.sahticonnect.dashboard.config.SahtiProperties;
import tn.sahticonnect.dashboard.service.RessourceIntrouvableException;

import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Client SOAP de dossier-cnam-service : WebServiceTemplate + classes JAXB générées depuis le XSD.
 * Délai de connexion et de lecture : 3 s. Fault PATIENT_INCONNU → NOT_FOUND ; toute autre panne →
 * ServiceIndisponibleException.
 */
@Component
public class CnamClient {

    private final WebServiceTemplate template;

    public CnamClient(SahtiProperties proprietes) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(ConsulterHistoriqueRequest.class.getPackageName());
        HttpUrlConnectionMessageSender transport = new HttpUrlConnectionMessageSender();
        transport.setConnectionTimeout(proprietes.timeout());
        transport.setReadTimeout(proprietes.timeout());
        this.template = new WebServiceTemplate(marshaller);
        this.template.setDefaultUri(proprietes.cnam().url());
        this.template.setMessageSender(transport);
    }

    /** consulterHistorique : vignette de l'assuré et historique de soins. */
    public HistoriqueCnam consulterHistorique(String patientId) {
        ConsulterHistoriqueRequest requete = new ConsulterHistoriqueRequest();
        requete.setIdentifiantPatient(patientId);
        ConsulterHistoriqueResponse reponse = (ConsulterHistoriqueResponse) appeler(requete, patientId);
        List<EntreeHistoriqueSoins> entrees = reponse.getEntrees().stream()
                .map(e -> new EntreeHistoriqueSoins(versLocalDate(e.getDate()), e.getEtablissement(), e.getDiagnostic(),
                        e.getActes().stream()
                                .map(a -> new ActeCote(a.getLettreCle().value(), a.getCoefficient().doubleValue(), a.getLibelle()))
                                .toList(),
                        e.getNumeroFeuilleSoins()))
                .toList();
        return new HistoriqueCnam(reponse.getAssure().getCodeVignetteCNAM(), entrees);
    }

    /** verifierDroitsCNAM pour une consultation « C 1 » aujourd'hui : droits ouverts et taux. */
    public Couverture verifierDroits(String patientId, String codeVignette) {
        tn.sahticonnect.dashboard.client.cnam.ws.ActeCote acte = new tn.sahticonnect.dashboard.client.cnam.ws.ActeCote();
        acte.setLettreCle(LettreCle.C);
        acte.setCoefficient(BigDecimal.ONE);
        VerifierDroitsCNAMRequest requete = new VerifierDroitsCNAMRequest();
        requete.setIdentifiantPatient(patientId);
        requete.setCodeVignetteCNAM(codeVignette);
        requete.setActe(acte);
        VerifierDroitsCNAMResponse reponse = (VerifierDroitsCNAMResponse) appeler(requete, patientId);
        return new Couverture(reponse.getCouverture().isCouvert(), reponse.getCouverture().getTauxRemboursement());
    }

    private Object appeler(Object requete, String patientId) {
        try {
            return template.marshalSendAndReceive(requete);
        } catch (SoapFaultClientException e) {
            if (e.getFaultStringOrReason() != null && e.getFaultStringOrReason().startsWith("Patient inconnu")) {
                throw new RessourceIntrouvableException("Patient " + patientId + " inconnu de la CNAM");
            }
            throw new ServiceIndisponibleException(ServiceIndisponibleException.DOSSIER_CNAM,
                    "fault « " + e.getFaultStringOrReason() + " »", e);
        } catch (RuntimeException e) {
            // connexion refusée, délai dépassé, réponse illisible…
            throw new ServiceIndisponibleException(ServiceIndisponibleException.DOSSIER_CNAM,
                    NestedExceptionUtils.getMostSpecificCause(e).getMessage(), e);
        }
    }

    private static LocalDate versLocalDate(XMLGregorianCalendar date) {
        return date == null ? null : LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }
}
