package tn.sahticonnect.orchestrateur.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.sahticonnect.orchestrateur.config.OrchestrateurProperties;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client GraphQL de dashboard-medical-service. Les requêtes sont dans src/main/resources/graphql-documents/
 * (un test les valide contre le schéma du dashboard). HttpSyncGraphQlClient : la variante synchrone
 * (RestClient) de HttpGraphQlClient, adaptée à un workflow bloquant exécuté sur des threads virtuels.
 * <ul>
 *   <li>erreur GraphQL NOT_FOUND / BAD_REQUEST → ErreurMetierException (jamais réessayée) ;</li>
 *   <li>service arrêté, délai de 3 s, autre erreur → erreur technique.</li>
 * </ul>
 */
@Component
public class DashboardClient {

    public static final String SERVICE = "dashboard-medical";

    /** Données de creerIntervention (noms = champs de CreerInterventionInput). */
    public record NouvelleIntervention(String patientId, String type, String alerteId, String description,
                                       String referenceExterne, String etat, String statutFacturation) {
    }

    /** Données de mettreAJourIntervention (null = inchangé). */
    public record Modification(String etat, String statutFacturation, String referenceExterne,
                               String numeroFeuilleSoins, BigDecimal montantTotal, BigDecimal partCNAM,
                               BigDecimal partPatient, String description) {
    }

    private final HttpSyncGraphQlClient client;

    public DashboardClient(OrchestrateurProperties proprietes, RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(proprietes.timeout())
                .build();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(http);
        fabrique.setReadTimeout(proprietes.timeout());
        this.client = HttpSyncGraphQlClient.builder(builder.requestFactory(fabrique).baseUrl(proprietes.dashboard().url()).build())
                .build();
    }

    /** enregistrerAlerte : idempotente côté dashboard (même id ALR-… → alerte existante). */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-idempotent")
    public void enregistrerAlerte(Map<String, Object> alerte) {
        executer("enregistrerAlerte", Map.of("input", alerte));
    }

    /** creerIntervention : création, réessayée seulement si la requête n'est pas partie. */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-non-idempotent")
    public String creerIntervention(NouvelleIntervention intervention) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("patientId", intervention.patientId());
        input.put("type", intervention.type());
        input.put("alerteId", intervention.alerteId());
        input.put("description", intervention.description());
        input.put("referenceExterne", intervention.referenceExterne());
        input.put("etat", intervention.etat());
        input.put("statutFacturation", intervention.statutFacturation());
        input.values().removeIf(java.util.Objects::isNull);
        return executer("creerIntervention", Map.of("input", input))
                .field("creerIntervention.id").toEntity(String.class);
    }

    /** mettreAJourIntervention : idempotente (mêmes valeurs → même résultat). */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-idempotent")
    public void mettreAJourIntervention(String interventionId, Modification modification) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("etat", modification.etat());
        input.put("statutFacturation", modification.statutFacturation());
        input.put("referenceExterne", modification.referenceExterne());
        input.put("numeroFeuilleSoins", modification.numeroFeuilleSoins());
        input.put("montantTotal", modification.montantTotal() == null ? null : modification.montantTotal().doubleValue());
        input.put("partCNAM", modification.partCNAM() == null ? null : modification.partCNAM().doubleValue());
        input.put("partPatient", modification.partPatient() == null ? null : modification.partPatient().doubleValue());
        input.put("description", modification.description());
        input.values().removeIf(java.util.Objects::isNull);
        executer("mettreAJourIntervention", Map.of("id", interventionId, "input", input));
    }

    /** resoudreAlerte (compensation de la saga) : idempotente (une alerte déjà RESOLUE est renvoyée telle quelle). */
    @CircuitBreaker(name = SERVICE)
    @Retry(name = "appel-idempotent")
    public void resoudreAlerte(String alerteId, String commentaire) {
        executer("resoudreAlerte", Map.of("id", alerteId, "commentaire", commentaire));
    }

    private ClientGraphQlResponse executer(String document, Map<String, Object> variables) {
        ClientGraphQlResponse reponse;
        try {
            reponse = client.documentName(document).variables(variables).executeSync();
        } catch (RuntimeException e) {
            throw Pannes.technique(SERVICE, e);
        }
        if (!reponse.getErrors().isEmpty()) {
            ResponseError erreur = reponse.getErrors().getFirst();
            Object code = erreur.getExtensions().get("code");
            if ("NOT_FOUND".equals(code) || "BAD_REQUEST".equals(code)) {
                throw new ErreurMetierException(SERVICE, code.toString(), erreur.getMessage());
            }
            throw new ReponseAbsenteException(SERVICE, "erreur GraphQL « " + erreur.getMessage() + " »", null);
        }
        if (!reponse.isValid()) {
            throw new ReponseAbsenteException(SERVICE, "réponse GraphQL sans données", null);
        }
        return reponse;
    }
}
