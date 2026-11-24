package tn.sahticonnect.orchestrateur.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tn.sahticonnect.orchestrateur.client.portail.api.RendezVousApi;
import tn.sahticonnect.orchestrateur.client.portail.api.UrgencesApi;
import tn.sahticonnect.orchestrateur.client.portail.invoker.ApiClient;
import tn.sahticonnect.orchestrateur.client.portail.model.DemandeAmbulanceDto;
import tn.sahticonnect.orchestrateur.client.portail.model.ErreurDto;
import tn.sahticonnect.orchestrateur.client.portail.model.MissionAmbulanceDto;
import tn.sahticonnect.orchestrateur.client.portail.model.RendezVousCreationDto;
import tn.sahticonnect.orchestrateur.client.portail.model.RendezVousDto;
import tn.sahticonnect.orchestrateur.client.portail.model.TypeConsultationDto;
import tn.sahticonnect.orchestrateur.config.OrchestrateurProperties;

import java.net.http.HttpClient;
import java.time.OffsetDateTime;

/**
 * Client REST de portail-patient-service, généré depuis docs/openapi/portail-patient.yaml
 * (openapi-generator, generator java, library restclient). Délais de connexion et de lecture : 3 s.
 * <ul>
 *   <li>réponse 4xx → ErreurMetierException avec le code du schéma Erreur (MISSION_DEJA_EN_COURS,
 *       AUCUN_VEHICULE_DISPONIBLE, AUCUN_CRENEAU_URGENCE…) : jamais réessayée ;</li>
 *   <li>5xx, délai dépassé, service arrêté → erreur technique.</li>
 * </ul>
 * Les deux appels sont des créations : retry « appel-non-idempotent » (seulement si la requête n'est pas partie).
 */
@Component
public class PortailClient {

    public static final String SERVICE = "portail-patient";

    public record Mission(String id, String vehicule, Integer delaiEstimeMinutes, String etablissementDestination) {
    }

    public record RendezVous(String id, String medecinId, OffsetDateTime debut) {
    }

    private final UrgencesApi urgences;
    private final RendezVousApi rendezVous;
    private final ObjectMapper objectMapper;

    public PortailClient(OrchestrateurProperties proprietes, RestClient.Builder builder, ObjectMapper objectMapper) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(proprietes.timeout())
                .build();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(http);
        fabrique.setReadTimeout(proprietes.timeout());
        ApiClient apiClient = new ApiClient(builder.requestFactory(fabrique).build());
        apiClient.setBasePath(proprietes.portail().url());
        this.urgences = new UrgencesApi(apiClient);
        this.rendezVous = new RendezVousApi(apiClient);
        this.objectMapper = objectMapper;
    }

    /** POST /urgences/ambulances (alerte CRITIQUE). */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-non-idempotent")
    public Mission demanderAmbulance(String patientId, String referenceAlerte, String motif) {
        DemandeAmbulanceDto demande = new DemandeAmbulanceDto()
                .identifiantPatient(patientId)
                .referenceAlerte(referenceAlerte)
                .motif(motif);
        try {
            MissionAmbulanceDto mission = urgences.demanderAmbulance(demande);
            return new Mission(mission.getId(), mission.getVehicule(), mission.getDelaiEstimeMinutes(),
                    mission.getEtablissementDestination());
        } catch (RuntimeException e) {
            throw traduire(e);
        }
    }

    /** POST /rendez-vous de type URGENCE : médecin traitant, premier créneau libre dans les 2 h. */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-non-idempotent")
    public RendezVous demanderRendezVousUrgence(String patientId, String referenceAlerte, String motif) {
        RendezVousCreationDto demande = new RendezVousCreationDto()
                .identifiantPatient(patientId)
                .type(TypeConsultationDto.URGENCE)
                .referenceAlerte(referenceAlerte)
                .motif(motif);
        try {
            RendezVousDto rdv = rendezVous.creerRendezVous(demande);
            return new RendezVous(rdv.getId(), rdv.getMedecinId(), rdv.getDebut());
        } catch (RuntimeException e) {
            throw traduire(e);
        }
    }

    private RuntimeException traduire(RuntimeException e) {
        if (e instanceof RestClientResponseException reponse && reponse.getStatusCode().is4xxClientError()) {
            ErreurDto erreur = lireErreur(reponse.getResponseBodyAsString());
            return new ErreurMetierException(SERVICE,
                    erreur != null && erreur.getCode() != null ? erreur.getCode() : "HTTP_" + reponse.getStatusCode().value(),
                    erreur != null && erreur.getMessage() != null ? erreur.getMessage() : reponse.getStatusText());
        }
        return Pannes.technique(SERVICE, e);
    }

    private ErreurDto lireErreur(String corps) {
        try {
            return corps == null || corps.isBlank() ? null : objectMapper.readValue(corps, ErreurDto.class);
        } catch (Exception illisible) {
            return null;
        }
    }
}
