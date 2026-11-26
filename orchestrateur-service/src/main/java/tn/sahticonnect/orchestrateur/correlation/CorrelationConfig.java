package tn.sahticonnect.orchestrateur.correlation;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Appels HTTP sortants faits avec RestClient : client REST du portail (généré) et client GraphQL du dashboard
 * (HttpSyncGraphQlClient, construit sur RestClient). Le RestClient.Builder fourni par Spring Boot applique ce
 * personnaliseur : chaque requête porte l'en-tête {@value Correlation#ENTETE} du traitement en cours.
 */
@Configuration
public class CorrelationConfig {

    /** Ajoute l'en-tête de corrélation (s'il y a un identifiant sur le thread appelant). */
    public static ClientHttpRequestInterceptor intercepteurRest() {
        return (requete, corps, execution) -> {
            String id = Correlation.courant();
            if (id != null) {
                requete.getHeaders().set(Correlation.ENTETE, id);
            }
            return execution.execute(requete, corps);
        };
    }

    @Bean
    public RestClientCustomizer propagationCorrelation() {
        return builder -> builder.requestInterceptor(intercepteurRest());
    }
}
