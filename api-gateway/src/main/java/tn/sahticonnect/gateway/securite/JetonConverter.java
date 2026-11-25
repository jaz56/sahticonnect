package tn.sahticonnect.gateway.securite;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Où lire le jeton :
 * <ul>
 *   <li>partout : en-tête {@code Authorization: Bearer …} ;</li>
 *   <li>SEULEMENT pour l'upgrade WebSocket de /api/graphql : paramètre {@code access_token} de l'URL, car l'API
 *       WebSocket du navigateur ne permet pas d'envoyer d'en-tête. Le paramètre est retiré avant de relayer
 *       ({@link PropagationIdentiteFilter}) et n'apparaît jamais dans les journaux (seul le chemin y est écrit).</li>
 * </ul>
 * Un {@code access_token} dans l'URL de toute autre requête est ignoré (la requête reste non authentifiée).
 */
public class JetonConverter implements ServerAuthenticationConverter {

    public static final String PARAMETRE = "access_token";
    public static final String CHEMIN_GRAPHQL = "/api/graphql";

    private final ServerBearerTokenAuthenticationConverter enTete = new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        ServerHttpRequest requete = exchange.getRequest();
        String jeton = requete.getQueryParams().getFirst(PARAMETRE);
        if (jeton != null && !jeton.isBlank() && estUpgradeWebSocketGraphQl(requete)) {
            return Mono.just(new BearerTokenAuthenticationToken(jeton));
        }
        return enTete.convert(exchange);
    }

    static boolean estUpgradeWebSocketGraphQl(ServerHttpRequest requete) {
        return requete.getMethod() == HttpMethod.GET
                && CHEMIN_GRAPHQL.equals(requete.getPath().pathWithinApplication().value())
                && "websocket".equalsIgnoreCase(requete.getHeaders().getFirst(HttpHeaders.UPGRADE));
    }
}
