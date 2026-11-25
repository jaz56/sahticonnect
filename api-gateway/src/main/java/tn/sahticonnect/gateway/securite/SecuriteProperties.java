package tn.sahticonnect.gateway.securite;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Sécurité de la gateway (préfixe {@code sahti.securite}).
 *
 * @param active       true : jetons OIDC exigés selon la matrice des droits ; false : tout est permis (développement
 *                     local sans Keycloak). CORS et suppression des en-têtes d'identité restent actifs dans les deux cas.
 * @param originesCors origines autorisées (le client Angular)
 */
@ConfigurationProperties(prefix = "sahti.securite")
public record SecuriteProperties(@DefaultValue("true") boolean active,
                                 @DefaultValue("http://localhost:4200") List<String> originesCors) {
}
