package tn.sahticonnect.cnam.endpoint;

import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import tn.sahticonnect.cnam.mapper.DossierCnamMapper;
import tn.sahticonnect.cnam.service.DossierCnamService;
import tn.sahticonnect.cnam.ws.ComparerGarantiesRequest;
import tn.sahticonnect.cnam.ws.ComparerGarantiesResponse;
import tn.sahticonnect.cnam.ws.ConsulterHistoriqueRequest;
import tn.sahticonnect.cnam.ws.ConsulterHistoriqueResponse;
import tn.sahticonnect.cnam.ws.EmettreFeuilleDeSoinsRequest;
import tn.sahticonnect.cnam.ws.EmettreFeuilleDeSoinsResponse;
import tn.sahticonnect.cnam.ws.VerifierDroitsCNAMRequest;
import tn.sahticonnect.cnam.ws.VerifierDroitsCNAMResponse;

import static tn.sahticonnect.cnam.config.WebServiceConfig.NAMESPACE;

/**
 * Endpoint SOAP : chaque méthode est associée, via @PayloadRoot, à l'élément racine
 * (namespace + nom local) du corps du message SOAP. Spring WS désérialise ce corps en objet
 * JAXB (@RequestPayload) puis sérialise l'objet renvoyé (@ResponsePayload).
 * L'endpoint ne contient aucune règle métier : mapper → service → mapper.
 */
@Endpoint
@RequiredArgsConstructor
public class DossierCnamEndpoint {

    private final DossierCnamService service;
    private final DossierCnamMapper mapper;

    @PayloadRoot(namespace = NAMESPACE, localPart = "verifierDroitsCNAMRequest")
    @ResponsePayload
    public VerifierDroitsCNAMResponse verifierDroitsCNAM(@RequestPayload VerifierDroitsCNAMRequest requete) {
        return mapper.versReponse(service.verifierDroits(
                requete.getIdentifiantPatient(),
                requete.getCodeVignetteCNAM(),
                mapper.versActe(requete.getActe()),
                mapper.versLocalDate(requete.getDateActe())));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "emettreFeuilleDeSoinsRequest")
    @ResponsePayload
    public EmettreFeuilleDeSoinsResponse emettreFeuilleDeSoins(@RequestPayload EmettreFeuilleDeSoinsRequest requete) {
        return mapper.versReponse(service.emettreFeuilleDeSoins(mapper.versDemande(requete)));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "consulterHistoriqueRequest")
    @ResponsePayload
    public ConsulterHistoriqueResponse consulterHistorique(@RequestPayload ConsulterHistoriqueRequest requete) {
        return mapper.versReponse(service.consulterHistorique(
                requete.getIdentifiantPatient(),
                mapper.versLocalDate(requete.getDateDebut()),
                mapper.versLocalDate(requete.getDateFin())));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "comparerGarantiesRequest")
    @ResponsePayload
    public ComparerGarantiesResponse comparerGaranties(@RequestPayload ComparerGarantiesRequest requete) {
        return mapper.versReponse(service.comparerGaranties(
                requete.getIdentifiantPatient(),
                mapper.versOffre(requete.getPriseEnChargeA()),
                mapper.versOffre(requete.getPriseEnChargeB())));
    }
}
